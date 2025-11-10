package com.performativ.api.examples.bulk;


import com.performativ.client.api.IngestionAsyncApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.core.Configuration;
import com.performativ.client.model.SetupAsyncIngestionTaskRequest;
import com.performativ.client.model.TenantAsyncIngestionssetupTask200Response;
import io.github.cdimascio.dotenv.Dotenv;

public class IngestReferenceDataEntities {


    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("../../")
                .ignoreIfMissing()
                .load();

        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBearerToken(dotenv.get("API_KEY"));
        apiClient.setBasePath(dotenv.get("API_BASE_URL"));
        IngestionAsyncApi client = new IngestionAsyncApi(apiClient);

        SetupAsyncIngestionTaskRequest asyncIngestionRequest = new SetupAsyncIngestionTaskRequest();
        asyncIngestionRequest.entity("clients");
        try {
            TenantAsyncIngestionssetupTask200Response tenantAsyncIngestionssetupTask200Response = client.tenantAsyncIngestionssetupTask(asyncIngestionRequest);
            String taskId = tenantAsyncIngestionssetupTask200Response.getTaskId();
            String uploadUrl = tenantAsyncIngestionssetupTask200Response.getPresignedUploadUrl();
            System.out.println("Upload URL: " + uploadUrl);
            System.out.println("Task ID: " + taskId);


        } catch (ApiException ex){
            System.out.println(ex.getMessage());
        }



    }

}
