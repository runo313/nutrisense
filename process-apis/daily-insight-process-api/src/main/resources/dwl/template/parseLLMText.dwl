%dw 2.0

fun attachLlmText(recommendations, crossSignals, body) = do {
  var recommendationsWithText= recommendations map ((rec) -> do {
    var match = body.text.recommendations filter ($.rank == rec.rank )
    ---
    rec update {
        case .text -> match.text[0]
    }
} )

	var crossSignalsWithText = crossSignals map ((sig) -> do {
		var match = (body.text.cross_signal filter ($.key == sig.key))
		---
		sig update {
	        case .text -> match.text[0]
	    }
	})
	---
	{ recommendations: recommendationsWithText, cross_signal: crossSignalsWithText }
}