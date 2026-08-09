%dw 2.0
fun buildLog(level, message,content,correlationId,flowName) = {
	"message": message,
	"flow": flowName,
    "level": level,
    "correlationId": correlationId,
    "appName": Mule::p('app.name'),
    "layer": Mule::p('wearable.layer'),
    "content": content,
    "timestamp": now() as String {format: "yyyy-MM-dd'T'HH:mm:ssXXX"},  
}