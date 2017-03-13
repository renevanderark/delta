package nl.kb.delta;

import io.dropwizard.testing.junit.DropwizardAppRule;
import nl.kb.dare.manifest.ManifestXmlHandler;
import nl.kb.dare.manifest.ObjectResource;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.xml.sax.SAXException;

import javax.ws.rs.core.Response;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class DepositIntegrationTest {
    private static final String APP_HOST = "localhost:4567";
    private static final String APP_URL = "http://" + APP_HOST;

    private static final SAXParser saxParser;

    static {
        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize sax parser", e);
        }
    }

    @ClassRule
    public static TestRule appRule;

    private static File getFileResource(String name) throws URISyntaxException {
        final URL resource = DepositIntegrationTest.class.getResource(name);
        if (resource == null) {
            return null;
        }
        return Paths.get(resource.toURI()).toFile();
    }

    private static String getUriResource(String name) throws URISyntaxException {
        return Paths.get(DepositIntegrationTest.class.getResource(name).toURI()).toString();
    }

    static {
        try {
            appRule = new DropwizardAppRule<>(App.class, getUriResource("/integration/integration.yaml"));
        } catch (URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private MultipartEntityBuilder getPackageBuilder(String packageName) throws IOException, SAXException, URISyntaxException {
        final InputStream manifest = DepositIntegrationTest.class.getResourceAsStream("/samples/" + packageName + "/manifest.xml");

        final ManifestXmlHandler manifestXmlHandler = new ManifestXmlHandler();

        saxParser.parse(manifest, manifestXmlHandler);


        @SuppressWarnings("ConstantConditions")
        final MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create()
                .addPart("manifest", new FileBody(getFileResource("/samples/" + packageName + "/manifest.xml")))
                .addPart("metadata", new FileBody(getFileResource("/samples/" + packageName + "/metadata.xml")));

        for (ObjectResource objectResource : manifestXmlHandler.getObjectResources()) {
            final String filePath = objectResource.getXlinkHref().replace("file://.", "/samples/" + packageName);
            final File fileResource = getFileResource(filePath);
            if (fileResource != null) {
                multipartEntityBuilder.addPart(objectResource.getId(), new FileBody(fileResource));
            }
        }

        return multipartEntityBuilder;
    }

    private HttpEntity getPackage(final String packageName) throws URISyntaxException, IOException, SAXException {
        return  getPackageBuilder(packageName).build();
    }

    @Test
    public void acceptsAValidPackage() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = getPackage("valid");

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(200));
        response.getEntity().writeTo(System.out);
    }

    @Test
    public void rejectsAPackageWithMoreThanOneFilePerId() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final MultipartEntityBuilder almostValid = getPackageBuilder("valid")
                .addBinaryBody("FILE_0001", new byte[] {});
        final HttpEntity multipartEntity = almostValid.build();

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(400));
        response.getEntity().writeTo(System.out);
    }

    @Test
    public void rejectsAPackageWithMissingFiles() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = getPackage("misses-file");

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }


    @Test
    public void rejectsAPackageWithChecksumMismatch() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = getPackage("checksum-mismatch");

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }


    @Test
    public void rejectsAPackageWithByteCountMismatch() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = getPackage("bytecount-mismatch");

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }


    @Test
    public void rejectsAPackageWithUnknownChecksumAlgorithm() throws IOException, URISyntaxException, SAXException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = getPackage("unknown-checksum-algorithm");

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }

    @Test
    public void rejectsAPackageWithMissingManifestEntries() throws SAXException, IOException, URISyntaxException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final MultipartEntityBuilder packageBuilder = getPackageBuilder("misses-manifest-entry");
        final HttpEntity multipartEntity = packageBuilder.addPart("FILE_0002",
                new FileBody(getFileResource("/samples/misses-manifest-entry/resources/shen.jpg")))
                .build();

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }

    @Test
    public void rejectsAPackageWithCorruptedManifest() throws SAXException, IOException, URISyntaxException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final HttpEntity multipartEntity = MultipartEntityBuilder
                .create().addBinaryBody("manifest", "<manif".getBytes()).build();

        final HttpPost httpPost = new HttpPost(APP_URL + "/deposit");
        httpPost.setEntity(multipartEntity);
        final HttpResponse response = httpClient.execute(httpPost);

        assertThat(response.getStatusLine().getStatusCode(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        response.getEntity().writeTo(System.out);
    }


}
