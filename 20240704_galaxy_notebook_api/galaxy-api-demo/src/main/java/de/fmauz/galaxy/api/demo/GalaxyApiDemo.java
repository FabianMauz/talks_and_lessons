package de.fmauz.galaxy.api.demo;

import com.github.jmchilton.blend4j.galaxy.GalaxyInstance;
import com.github.jmchilton.blend4j.galaxy.GalaxyInstanceFactory;
import com.github.jmchilton.blend4j.galaxy.HistoriesClient;
import com.github.jmchilton.blend4j.galaxy.ToolDataClient;
import com.github.jmchilton.blend4j.galaxy.ToolsClient;
import com.github.jmchilton.blend4j.galaxy.beans.Dataset;
import com.github.jmchilton.blend4j.galaxy.beans.History;
import com.github.jmchilton.blend4j.galaxy.beans.OutputDataset;
import com.github.jmchilton.blend4j.galaxy.beans.Tool;
import com.github.jmchilton.blend4j.galaxy.beans.ToolExecution;
import com.github.jmchilton.blend4j.galaxy.beans.ToolInputs;
import com.github.jmchilton.blend4j.galaxy.beans.ToolSection;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

/**
 *
 * @author fmauz
 */
public class GalaxyApiDemo {

    static final String GALAXY_URL = "https://test.galaxyproject.org/";
    static final String TOOL_NAME = "Sort";
    static final String LOCAL_FILE_NAME = "20240703_top80_german_cities.csv";
    static final String HISTORY_NAME = "myHistory";

    public static void main(String[] args) throws Exception {
        //Connect to Galaxy instance
        String apiKey = Files.readString(Paths.get("src/main/resources/api-key.txt"));
        GalaxyInstance galaxyInstance = GalaxyInstanceFactory.get(GALAXY_URL, apiKey);

        //Get history to work in
        History myHistory = getMyHistory(galaxyInstance);

        //Trigger dataset upload
        String datasetId = uploadDataSet(myHistory, galaxyInstance);

        //Wait until dataset is processed at galaxy
        Dataset dataset = waitUntilDatasetReady(datasetId, galaxyInstance, myHistory.getId());

        //Get the sorting tool
        Tool tool = getSortTool(galaxyInstance);

        //Define the inputs of the tool
        ToolInputs inputs = prepareInputParameter(tool, dataset);

        //Trigger the job/tool and get the created dataset (not processed at that moment)
        OutputDataset output = galaxyInstance
                .getToolsClient()
                .create(myHistory, inputs)
                .getOutputs()
                .get(0);

        //Wait until dataset is processed at galaxy
        waitUntilDatasetReady(output.getId(), galaxyInstance, myHistory.getId());

        
        //Download the finished dataset. I do not use the java API here but use 
        //the direct http call to for demonstration purpose.
        String downloadHttpUrl = GALAXY_URL + "api/datasets/" + output.getId()
                + "/display?to_ext=csv&key=" + apiKey;

        URL url = new URL(downloadHttpUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        InputStream responseStream = con.getInputStream();
        Files.copy(responseStream, Path.of("./response.csv"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static ToolInputs prepareInputParameter(Tool tool, Dataset dataset) {
        ToolInputs inputs = new ToolInputs(tool.getId(), new HashMap<>());
        inputs.getInputs().put("input1", String.valueOf(dataset.getHid()));
        inputs.getInputs().put("style", "num");
        inputs.getInputs().put("order", "ASC");
        inputs.getInputs().put("column", "1");
        return inputs;
    }

    public static History getMyHistory(GalaxyInstance galaxyInstance) {
        HistoriesClient historyClient = galaxyInstance.getHistoriesClient();
        for (History h : historyClient.getHistories()) {
            if (h.getName().equals(HISTORY_NAME)) {
                return h;
            }
        }
        throw new RuntimeException("No history with name " + HISTORY_NAME + "found");
    }

    public static Tool getSortTool(GalaxyInstance galaxyInstance) {
        for (ToolSection t : galaxyInstance.getToolsClient().getTools()) {
            if (t.getName() == null) {
                continue;
            }
            for (Tool tool : t.getElems()) {
                if (tool.getName() == null) {
                    continue;
                }
                if (tool.getName().equals(TOOL_NAME)) {
                    return tool;
                }
            }
        }
        throw new RuntimeException("Tool was not found");
    }

    public static String uploadDataSet(History myHistory, GalaxyInstance galaxyInstance) {
        // create Galaxy Library
        File fileToUpload = new File("src/main/resources/" + LOCAL_FILE_NAME);
        ToolsClient.FileUploadRequest upload = new ToolsClient.FileUploadRequest(myHistory.getId(), fileToUpload);
        ToolExecution execution = galaxyInstance.getToolsClient().upload(upload);
        return execution.getOutputs().get(0).getId();
    }

    public static Dataset waitUntilDatasetReady(String datasetId, GalaxyInstance galaxyInstance, String historyId) throws InterruptedException {
        Dataset dataset;
        do {
            dataset = galaxyInstance.getHistoriesClient().showDataset(historyId, datasetId);
            System.out.println(dataset.getState());
            Thread.sleep(1000);
        } while (!dataset.getState().equals("ok"));
        return dataset;
    }
}
