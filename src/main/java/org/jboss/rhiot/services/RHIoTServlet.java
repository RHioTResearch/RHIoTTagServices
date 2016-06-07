package org.jboss.rhiot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Expose some of the RHIoTTagScanner information via REST
 */
@SuppressWarnings("PackageAccessibility")
public class RHIoTServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private RHIoTTagScanner scanner;
    private PreferencesService preferencesService;

    RHIoTServlet(RHIoTTagScanner scanner, PreferencesService preferencesService) {
        this.scanner = scanner;
        this.preferencesService = preferencesService;
    }

    public void setPreferencesService(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonParser parser = new  JsonParser();
        JsonElement json = parser.parse(req.getReader());
        JsonArray array = json.getAsJsonArray();
        Preferences prefs = null;
        if(preferencesService != null )
            prefs = preferencesService.getUserPreferences(RHIoTServlet.class.getSimpleName());
        for(int n = 0; n < array.size(); n ++) {
            JsonObject info = array.get(n).getAsJsonObject();
            String name = info.get("name").getAsString();
            String address = info.get("address").getAsString();
            if(prefs != null)
                prefs.put(address, name);
            scanner.updateTagInfo(address, name);
        }
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.printf("RHIoTService: %s\n", req.getPathInfo());

        resp.setContentType("application/json");
        Map<String,String> infos = scanner.getTags();
        System.out.printf("\tTag count: %d\n", infos.size());
        JsonArray jsonArray = new JsonArray();
        for(String addressKey : infos.keySet()) {
            JsonObject je = new JsonObject();
            String name = infos.get(addressKey);
            je.addProperty("address", addressKey);
            je.addProperty("name", name);
            System.out.printf("\t\tAddress: %s; name: %s\n", addressKey, name);
            jsonArray.add(je);
        }
        Gson gson = new GsonBuilder().create();
        String jsonOutput  = gson.toJson(jsonArray);
        resp.getWriter().write(jsonOutput);
        System.out.printf("\tTags: %s\n", jsonOutput);
    }
}
