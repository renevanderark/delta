package nl.kb.delta.endpoints;

import nl.kb.delta.deposits.Deposit;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.xml.sax.SAXException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;

@Path("/deposit")
public class DepositEndpoint {



    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deposit(
            @FormDataParam("manifest") InputStream manifest,
            FormDataMultiPart files) throws ParserConfigurationException, SAXException {

            return new Deposit()
                .onSuccess(result -> Response.ok(result).build())
                .onFailure(result -> Response.status(Response.Status.BAD_REQUEST).entity(result).build())
                .prepare(manifest)
                .ingest(files)
                .buildResponse();

    }
}
