import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.nutrisense.trendanalysis.evaluation.insights.TrendEvaluationService;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdRepository;

public class TrendEvaluationRunner {

    public static void main(String[] args) throws IOException {
        String request = ThresholdRepository.readResourceAsString("/requests/trend-request-window7.json");
        String response = TrendEvaluationService.evaluateJson(request);

        Gson pretty = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        System.out.println(pretty.toJson(JsonParser.parseString(response)));
    }
}