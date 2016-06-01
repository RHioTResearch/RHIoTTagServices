package org.jboss.rhiot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jboss.rhiot.services.api.ILaserTag;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * Expose some of the RHIoTTagScanner information via REST
 */
public class RHIoTServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private RHIoTTagScanner scanner;

    RHIoTServlet(RHIoTTagScanner scanner) {
        this.scanner = scanner;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.printf("RHIoTService: %s\n", req.getPathInfo());

        resp.setContentType("application/json");
        Collection<ILaserTag> infos = scanner.getTags();
        System.out.printf("\tTag count: %d\n", infos.size());
        JsonArray jsonArray = new JsonArray();
        for(ILaserTag tag : infos) {
            JsonObject je = new JsonObject();
            String addressKey = Utils.toString(tag.getTagAddress());
            je.addProperty("address", addressKey);
            System.out.printf("\t\tAddress: %s\n", addressKey);
            jsonArray.add(je);
        }
        Gson gson = new GsonBuilder().create();
        String jsonOutput  = gson.toJson(jsonArray);
        resp.getWriter().write(jsonOutput);
        System.out.printf("\tTags: %s\n", jsonOutput);
    }
}
