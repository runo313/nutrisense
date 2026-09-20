%dw 2.0

fun buildInsightPrompt(recommendations, crossSignals) = do {
  var recommendationsText = recommendations map ((r, idx) ->
    "Item " ++ ((idx + 1) as String) ++ " (rank " ++ (r.rank as String) ++ "):\n" ++
    "- dimension: " ++ r.dimension ++ "\n" ++
    "- driving_metric: " ++ r.driving_metric ++ "\n" ++
    "- severity: " ++ r.severity ++ "\n" ++
    "- condition_context: " ++ (r.condition_context default "none") ++ "\n" ++
    "- detail: " ++ (write(r.detail, "application/json"))
  ) joinBy "\n\n"

  var crossSignalsText = if (isEmpty(crossSignals)) "None fired today."
    else crossSignals map ((c) ->
      "- key: " ++ c.key ++ ", signals_involved: " ++ (write(c.signals_involved, "application/json"))
    ) joinBy "\n"

  var instructions =
    "You are writing short, factual health insight text for a daily nutrition and wellness app. " ++
    "You will be given a list of flagged recommendation items and any cross-signal combinations that fired today. " ++
    "Write one sentence of text per item, in a calm and factual tone. " ++
    "Rules you must follow strictly:\n" ++
    "1. Never diagnose, suggest medication, or interpret clinical results. Only describe what was observed today.\n" ++
    "2. Never imply a trend, pattern, or history. This system has no memory of any other day. Only describe today.\n" ++
    "3. For sleep percentage metrics (sleep_rem_pct, sleep_deep_pct), do not make physiological claims about what the percentage means. State the number and the reference range only.\n" ++
    "4. For recovery metrics (resting_hr_bpm, hrv_sdnn), do not make physiological claims. State the number and the personal baseline only.\n" ++
    "5. If condition_context is present, you may reference it naturally, but do not exaggerate its significance.\n" ++
    "6. Return your response as strict JSON only, no markdown, no commentary, in this exact shape:\n" ++
    "{ \"recommendations\": [{ \"rank\": <number>, \"text\": \"<string>\" }], \"cross_signal\": [{ \"key\": \"<string>\", \"text\": \"<string>\" }] }\n" ++
    "Every recommendation rank must have exactly one corresponding text entry. Every cross-signal key must have exactly one corresponding text entry."

  ---
  instructions ++ "\n\nRECOMMENDATIONS:\n\n" ++ recommendationsText ++ "\n\nCROSS-SIGNALS:\n\n" ++ crossSignalsText
}