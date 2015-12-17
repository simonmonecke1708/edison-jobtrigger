package de.otto.edison.jobtrigger.discovery;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import de.otto.edison.jobtrigger.definition.JobDefinition;
import de.otto.edison.registry.service.RegisteredService;
import de.otto.edison.registry.service.Registry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static de.otto.edison.registry.api.Link.link;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryServiceTest {

    public static final String ENV_NAME = "someEnv";
    public static final String DEFAULT_TRIGGER_URL = "someTriggerUrl";

    @InjectMocks
    DiscoveryService discoveryService;

    @Mock
    private AsyncHttpClient httpClient;

    @Mock
    private Registry serviceRegistry;

    @Before
    public void setUp() throws Exception {
        reset(httpClient, serviceRegistry);
    }

    @Test
    public void shouldConvertToJobDefinitions() throws IOException {
        RegisteredService service = someService();
        JobDefinitionRepresentation jobDefinitionRepresentation = someJobDefinitionRepresentation();

        Response response = mock(Response.class);

        when(response.getResponseBody()).thenReturn(
                new Gson().toJson(jobDefinitionRepresentation)
        );
        JobDefinition jd = discoveryService.jobDefinitionFrom("someDefinitionUrl", service, response);

        assertThat(discoveryService, is(not(nullValue())));
        assertThat(jd.getCron(), is(Optional.of("* * * * * *")));
        assertThat(jd.getDefinitionUrl(), is("someDefinitionUrl"));
        assertThat(jd.getDescription(), is("MyJob"));
        assertThat(jd.getEnv(), is(ENV_NAME));
        assertThat(jd.getFixedDelay(), is(Optional.of(Duration.ofSeconds(12L))));
        assertThat(jd.getJobType(), is("someType"));
        assertThat(jd.getRetries(), is(12));
        assertThat(jd.getRetryDelay(), is(Optional.of(Duration.ofSeconds(12L))));
        assertThat(jd.getService(), is("myService"));
        assertThat(jd.getTriggerUrl(), is(DEFAULT_TRIGGER_URL));
    }

    @Ignore("Ignored until Validation is implemented")
    @Test
    public void shouldValidateResponseBody() throws IOException, ExecutionException, InterruptedException {
        RegisteredService service = someService();

        Response response = mock(Response.class);
        when(response.getResponseBody()).thenReturn("");
        stubHttpResponse(response);

        JobDefinition jobDefintion = discoveryService.jobDefinitionFrom("someDefinitionUrl", service, response);

        assertThat(jobDefintion, is(not(nullValue())));
    }

    @Test
    public void shouldDiscoverJobDefinitionsURLsForEveryService() throws Exception {
        when(serviceRegistry.findServices())
                .thenReturn(ImmutableList.of(someService(), someService()));
        stubHttpResponse(mock(Response.class));

        discoveryService.rediscover();

        verify(httpClient, times(2)).prepareGet("someHref/internal/jobdefinitions");
    }

    @Test
    public void shouldFetchJobDefinitionsForAllJobs() throws Exception {
        Response singleJobDefinitionResponse = mock(Response.class);
        when(singleJobDefinitionResponse.getStatusCode()).thenReturn(200);
        when(singleJobDefinitionResponse.getResponseBody()).thenReturn(
                new Gson().toJson(someJobDefinitionRepresentation())
        );
        stubHttpResponse(singleJobDefinitionResponse);

        List<JobDefinition> jobDefinitions = discoveryService.jobDefinitionsFrom(someService(), jobDefinitionLinksResponse());

        verify(httpClient).prepareGet("someHref/myJobDefinitions");
        verify(httpClient).prepareGet("someOtherHref/myJobDefinitions");
        assertThat(jobDefinitions, hasSize(2));
        assertThat(jobDefinitions.get(0).getTriggerUrl(), is(DEFAULT_TRIGGER_URL));
    }

    @Test
    public void shouldNotFetchSingleJobDefinitionWhenReceivingErrorResponse() throws Exception {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatusCode()).thenReturn(400);
        when(errorResponse.getResponseBody()).thenReturn("");
        stubHttpResponse(errorResponse);

        discoveryService.jobDefinitionsFrom(someService(), jobDefinitionLinksResponse());

        verify(httpClient, times(2)).prepareGet(anyString());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void shouldCatchIOExceptionOnResponseError() throws Exception {
        Response errorThrowingResponse = mock(Response.class);
        when(errorThrowingResponse.getResponseBody()).thenThrow(new IOException("Expected Exception"));

        List<JobDefinition> jobDefinitions = discoveryService.jobDefinitionsFrom(someService(), errorThrowingResponse);

        assertThat(jobDefinitions, hasSize(0));
    }

    @Test
    @Ignore("Ignored until bug is fixed")
    public void shouldIgnoreNullValuesInJobDefinitionsList() throws Exception {
        JobDefinitionRepresentation jobDefinitionRepresentation = someJobDefinitionRepresentation();
        jobDefinitionRepresentation.setLinks(ImmutableList.of());

        Response singleJobDefinitionResponse = mock(Response.class);

        when(singleJobDefinitionResponse.getResponseBody()).thenReturn(
                new Gson().toJson(jobDefinitionRepresentation)
        );

        stubHttpResponse(singleJobDefinitionResponse);

        List<JobDefinition> jobDefinitions = discoveryService.jobDefinitionsFrom(someService(), jobDefinitionLinksResponse());

        assertThat(jobDefinitions, hasSize(0));
    }

    @Test
    public void shouldCallListenerForAllServices() throws Exception {
        when(serviceRegistry.findServices()).thenReturn(ImmutableList.of(someService()));

        Response serviceResponse = jobDefinitionLinksResponse();
        when(serviceResponse.getStatusCode()).thenReturn(200);

        stubHttpResponse("someHref/internal/jobdefinitions", serviceResponse);

        Response singleJobDefinitionResponse = mock(Response.class);
        when(singleJobDefinitionResponse.getStatusCode()).thenReturn(200);
        when(singleJobDefinitionResponse.getResponseBody()).thenReturn(
                new Gson().toJson(someJobDefinitionRepresentation())
        );
        stubHttpResponse("someHref/myJobDefinitions", singleJobDefinitionResponse);
        stubHttpResponse("someOtherHref/myJobDefinitions", singleJobDefinitionResponse);

        DiscoveryListener listenerMock = mock(DiscoveryListener.class);
        discoveryService.register(listenerMock);
        discoveryService.rediscover();

        verify(listenerMock).updatedJobDefinitions();
    }

    private void stubHttpResponse(Response response) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        stubHttpResponse(null, response);
    }

    // manual deep stubbing is necessary because PowerMock does not support deep stubbing automatically
    @SuppressWarnings("unchecked")
    private void stubHttpResponse(String url, Response response) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        AsyncHttpClient.BoundRequestBuilder requestBuilderStub = mock(AsyncHttpClient.BoundRequestBuilder.class);
        ListenableFuture<Response> listenableFutureStub = mock(ListenableFuture.class);
        when(httpClient.prepareGet(url == null ? anyString() : url)).thenReturn(requestBuilderStub);
        when(requestBuilderStub.setHeader(anyString(), anyString())).thenReturn(requestBuilderStub);
        when(requestBuilderStub.execute()).thenReturn(listenableFutureStub);
        when(listenableFutureStub.get()).thenReturn(response);
    }

    private Response jobDefinitionLinksResponse() throws IOException {
        LinksRepresentation linksRepresentation = new LinksRepresentation();
        linksRepresentation.setLinks(ImmutableList.of(
                link(DiscoveryService.JOB_DEFINITION_LINK_RELATION_TYPE, "someHref/myJobDefinitions", "someTitle"),
                link(DiscoveryService.JOB_DEFINITION_LINK_RELATION_TYPE, "someOtherHref/myJobDefinitions", "someOtherTitle")));

        Response jobDefinitionsResponse = mock(Response.class);
        when(jobDefinitionsResponse.getResponseBody()).thenReturn(
                new Gson().toJson(linksRepresentation)
        );
        return jobDefinitionsResponse;
    }

    private JobDefinitionRepresentation someJobDefinitionRepresentation() {
        JobDefinitionRepresentation jobDefinitionRepresentation = new JobDefinitionRepresentation();
        jobDefinitionRepresentation.setCron("* * * * * *");
        jobDefinitionRepresentation.setFixedDelay(12L);
        jobDefinitionRepresentation.setLinks(ImmutableList.of(link("http://github.com/otto-de/edison/link-relations/job/trigger", DEFAULT_TRIGGER_URL, "title")));
        jobDefinitionRepresentation.setName("MyJob");
        jobDefinitionRepresentation.setRetries(12);
        jobDefinitionRepresentation.setRetryDelay(12L);
        jobDefinitionRepresentation.setType("someType");
        return jobDefinitionRepresentation;
    }

    private RegisteredService someService() {
        return new RegisteredService(
                "myService",
                "someHref",
                "someDescription",
                Duration.ZERO,
                "someEnv",
                ImmutableList.of());
    }
}