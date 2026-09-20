%dw 2.0
import try from dw::Runtime
// 1. Get the model text; skip "thought" parts, tolerate missing fields
fun extractLlmText(res) = do {
    var candidate = res.candidates[0]
    var parts = (candidate.content.parts default []) filter (not ($.thought default false))
    ---
    {
        finishReason: candidate.finishReason default "MISSING",
        text: (parts map ($.text default "")) joinBy ""
    }
}

// 2. Keep only the span from the first { or [ to the last } or ].
//    Drops ```json fences and any "Here's your JSON:" chatter.
fun cleanLlmText(raw: String): String =
    (trim(raw) match /^[^\{\[]*([\{\[][\s\S]*[\}\]])[^\}\]]*$/)[1] default ""
    
fun safeParse(text: String) = do {
    var attempt = try(() -> read(text, "application/json"))
    ---
    if (attempt.success) { ok: true, value: attempt.result }
    else { ok: false, reason: "Invalid JSON: " ++ (attempt.error.message default "unknown") }
}

fun attachLlmText(recommendations, crossSignals, llmParsed) = do {
  var recommendationsWithText = recommendations map ((rec) -> do {
    var match = (llmParsed.recommendations filter ($.rank == rec.rank))[0]
    ---
    rec ++ { text: if (match != null) match.text else null }
  })
  var crossSignalsWithText = crossSignals map ((sig) -> do {
    var match = (llmParsed.cross_signal filter ($.key == sig.key))[0]
    ---
    sig ++ { text: if (match != null) match.text else null }
  })
  ---
  { recommendations: recommendationsWithText, cross_signal: crossSignalsWithText }
}