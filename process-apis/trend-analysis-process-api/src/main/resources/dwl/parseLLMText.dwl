%dw 2.0
fun attachLlmText(recommendations, body) = do {
	var recommendationsWithText= recommendations map ((rec) -> do {
		var match = body.text.conclusions filter ($.rank == rec.rank)
		---
		rec update {
			case .text -> match.text[0]
		}
	} )
	---
	{
		recommendations: recommendationsWithText
	}
}
