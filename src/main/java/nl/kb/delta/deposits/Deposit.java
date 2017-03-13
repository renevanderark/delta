package nl.kb.delta.deposits;

import nl.kb.dare.checksum.ByteCountOutputStream;
import nl.kb.dare.checksum.ChecksumOutputStream;
import nl.kb.dare.io.InputStreamSplitter;
import nl.kb.dare.manifest.ManifestXmlHandler;
import nl.kb.dare.manifest.ObjectResource;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.xml.sax.SAXException;

import javax.ws.rs.core.Response;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Deposit {
    private Function<DepositResult, Response> successConsumer;
    private Function<DepositResult, Response> failureConsumer;
    private final DepositResult depositResult;
    private final SAXParser saxParser;
    private List<ObjectResource> objectResources = null;

    public Deposit() throws ParserConfigurationException, SAXException {
        saxParser = SAXParserFactory.newInstance().newSAXParser();

        depositResult = new DepositResult();
    }

    public Deposit onSuccess(Function<DepositResult, Response> successConsumer) {
        this.successConsumer = successConsumer;
        return this;
    }

    public Deposit onFailure(Function<DepositResult, Response> failureConsumer) {
        this.failureConsumer = failureConsumer;
        return this;
    }

    public Deposit prepare(InputStream manifest) {
        final ManifestXmlHandler manifestXmlHandler = new ManifestXmlHandler();

        try {
            saxParser.parse(manifest, manifestXmlHandler);
            objectResources = manifestXmlHandler.getObjectResourcesIncludingMetadata();
        } catch (SAXException | IOException e) {
            depositResult.addMessage(e.getMessage());
        }

        return this;
    }

    public Deposit ingest(FormDataMultiPart files) {
        if (objectResources == null) {
            return this;
        }

        if (!validateAllFilesPresent(files)) {
            return this;
        }

        if (!validateAllFilesInManifest(files)) {
            return this;
        }

        if (!saveAndVerify(files)) {
            return this;
        }

        return this;
    }

    private boolean saveAndVerify(FormDataMultiPart files) {

        final Set<Map.Entry<String, List<FormDataBodyPart>>> entries = files.getFields().entrySet();
        entries.removeIf(entry -> entry.getKey().equals("manifest"));

        for (Map.Entry<String, List<FormDataBodyPart>> entry : entries) {
            if (entry.getValue().size() != 1) { logInvalidFileCount(entry); continue; }

            final ObjectResource objectResource = getObjectResourceForFileEntry(entry);

            try {

                final InputStream is = entry.getValue().get(0).getValueAs(InputStream.class);
                final ByteCountOutputStream countOut = new ByteCountOutputStream();
                final ChecksumOutputStream checksumOut = new ChecksumOutputStream(objectResource.getChecksumType());
                new InputStreamSplitter(is, countOut, checksumOut).copy();

                if (objectResource.getSize() != countOut.getCurrentByteCount()) {
                    depositResult.addMessage(String.format(
                            "Byte count mismatch with manifest for file %s (expected=%d, actual=%d)",
                            entry.getKey(),
                            objectResource.getSize(),
                            countOut.getCurrentByteCount()
                    ));
                }

                if (!objectResource.getChecksum().equals(checksumOut.getChecksumString())) {
                    depositResult.addMessage(String.format(
                            "Checksum mismatch with manifest for file %s (expected=%s, actual=%s)",
                            entry.getKey(),
                            objectResource.getChecksum(),
                            checksumOut.getChecksumString()
                    ));
                }

            } catch (NoSuchAlgorithmException e) {
                depositResult.addMessage(String.format("Checksum algorithm not supported for file: %s, %s",
                    entry.getKey(), objectResource.getChecksumType()
                ));
            } catch (IOException e) {
                depositResult.addMessage(String.format("Failed to process file: %s, %s",
                        entry.getKey(), e.getMessage()
                ));
            }
        }


        return depositResult.isStillSuccesful();
    }

    private void logInvalidFileCount(Map.Entry<String, List<FormDataBodyPart>> entry) {
        depositResult.addMessage(String.format("File upload entry %s contains %d files, expected is 1",
                entry.getKey(), entry.getValue().size()));
    }

    private ObjectResource getObjectResourceForFileEntry(Map.Entry<String, List<FormDataBodyPart>> entry) {
        return objectResources.stream()
                .filter(res -> res.getId().equals(entry.getKey())).iterator().next();
    }

    // validate all present files are part of manifest
    private boolean validateAllFilesInManifest(FormDataMultiPart files) {
        depositResult.addMessages(
            files.getFields().keySet().stream()
                .filter(key -> !key.equals("manifest"))
                .filter(key -> objectResources.stream().map(ObjectResource::getId).noneMatch(id -> id.equals(key)))
                .map(key -> "Uploaded file is missing from manifest: " + key)
        );

        return depositResult.isStillSuccesful();
    }

    // validate all files are present
    private boolean validateAllFilesPresent(FormDataMultiPart files) {
        depositResult.addMessages(
            objectResources.stream()
                .filter(objectResource -> !files.getFields().containsKey(objectResource.getId()))
                .map(ObjectResource::getId)
                .map(id -> "Missing uploaded file expected from manifest: " + id)
        );

        return depositResult.isStillSuccesful();
    }

    public Response buildResponse() {
        if (depositResult.isStillSuccesful()) {
            return  successConsumer.apply(depositResult);
        } else {
            return failureConsumer.apply(depositResult);
        }
    }
}
