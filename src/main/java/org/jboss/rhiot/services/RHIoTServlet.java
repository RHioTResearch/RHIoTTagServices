package org.jboss.rhiot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jboss.rhiot.ble.bluez.RHIoTTag;
import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.jboss.rhiot.services.fsm.GameStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Expose some of the RHIoTTagScanner information via REST
 */
@SuppressWarnings("PackageAccessibility")
public class RHIoTServlet extends HttpServlet {
   private static final long serialVersionUID = 1L;
   private static final Logger log = LoggerFactory.getLogger(RHIoTServlet.class);

   private RHIoTTagScanner scanner;
   private String cloudPassword;

   public RHIoTServlet(RHIoTTagScanner scanner) {
      this.scanner = scanner;
   }

   public String getCloudPassword() {
      return cloudPassword;
   }

   public void setCloudPassword(String cloudPassword) {
      this.cloudPassword = cloudPassword;
   }

   /**
    * PUT endpoint for injecting tag data
    * @param req - request object
    * @param resp - response object
    * @throws ServletException
    * @throws IOException
    */
   @Override
   protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      JsonParser parser = new JsonParser();
      JsonElement json = parser.parse(req.getReader());
      JsonObject jsonObj = json.getAsJsonObject();
      String address = jsonObj.get("address").getAsString();
      byte keys = jsonObj.get("keys").getAsByte();
      int lux = jsonObj.get("lux").getAsInt();

      String name = scanner.getTagInfo(address);
      if(name == null) {
         resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Tag address has no assigned name");
      } else {
         RHIoTTag tag = new RHIoTTag(address, keys, lux);
         tag.setName(name+"Sim");
         CompletableFuture<GameStateMachine.GameState> future = scanner.handleTagAsync(tag);
         GameStateMachine.GameState state = null;
         try {
            state = future.get();
            resp.setContentType("application/txt");
            resp.getWriter().write(state.name());
         } catch (Exception e) {
            log.error("Failed to handle put for tag: "+address, e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
         }
      }
   }

   /**
    * POST endpoint for adding a tag name to address mapping. This requires a json array of name,address pairs.
    * @param req - request object
    * @param resp - response object
    * @throws ServletException
    * @throws IOException
    */
   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      JsonParser parser = new JsonParser();
      JsonElement json = parser.parse(req.getReader());
      JsonArray array = json.getAsJsonArray();
      for (int n = 0; n < array.size(); n++) {
         JsonObject info = array.get(n).getAsJsonObject();
         String name = info.get("name").getAsString();
         String address = info.get("address").getAsString();
         scanner.updateTagInfo(address, name);
      }
      resp.setStatus(HttpServletResponse.SC_OK);
   }

   /**
    * Entry point for handling the simple GET type of REST calls
    * @param req - request object
    * @param resp - response object
    * @throws ServletException
    * @throws IOException
    */
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      String pathInfo = req.getPathInfo();
      log.info(String.format("doGet(%s)\n", pathInfo));
      int status = HttpServletResponse.SC_OK;
      if (pathInfo.startsWith(IRHIoTTagScanner.CLOUD_PW_PATH))
         sendPassword(resp);
      else if (pathInfo.startsWith(IRHIoTTagScanner.TAG_INFO_PATH))
         sendTagInfo(resp);
      else if (pathInfo.startsWith(IRHIoTTagScanner.GAMESM_DIGRAPH_PATH))
         sendGameSMDigraph(req, resp);
      else if (pathInfo.startsWith(IRHIoTTagScanner.GAMESM_INFO_PATH))
         sendGameSMInfo(req, resp);
      else
         status = HttpServletResponse.SC_BAD_REQUEST;
      if (status != HttpServletResponse.SC_OK)
         resp.sendError(status);
      else
         resp.setStatus(status);
   }

   /**
    * Send the cloud account password
    * @param resp
    * @throws IOException
    */
   private void sendPassword(HttpServletResponse resp) throws IOException {
      resp.setContentType("application/txt");
      resp.getWriter().write(cloudPassword);
   }

   /**
    * Generate and send the game state machine digraph for plotting via graphviz
    * @param req - request object
    * @param resp - response object
    * @throws IOException
    */
   private void sendGameSMDigraph(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String address = req.getParameter("address");
      resp.setContentType("application/txt");
      GameStateMachine gsm = scanner.getGameSM(address);
      String digraph = gsm.exportAsString();
      resp.getWriter().write(digraph);
   }

   /**
    * Return the current game state and trigger a game information message
    * @param req - request object
    * @param resp - response object
    * @throws IOException
    */
   private void sendGameSMInfo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String address = req.getParameter("address");
      if(address == null) {
         resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No address parameter given");
      } else {
         resp.setContentType("application/txt");
         String state = scanner.getAndPublishGameSMInfo(address);
         resp.getWriter().write(state);
      }
   }

   /**
    * Return a json representation of the registered tag address to name mappings
    * @param resp - response object
    * @throws IOException
    */
   private void sendTagInfo(HttpServletResponse resp) throws IOException {
      resp.setContentType("application/json");
      Map<String, String> infos = scanner.getTags();
      log.debug(String.format("\tTag count: %d\n", infos.size()));
      JsonArray jsonArray = new JsonArray();
      for (String addressKey : infos.keySet()) {
         JsonObject je = new JsonObject();
         String name = infos.get(addressKey);
         je.addProperty("address", addressKey);
         je.addProperty("name", name);
         log.debug(String.format("\t\tAddress: %s; name: %s\n", addressKey, name));
         jsonArray.add(je);
      }
      Gson gson = new GsonBuilder().create();
      String jsonOutput = gson.toJson(jsonArray);
      resp.getWriter().write(jsonOutput);
      log.debug(String.format("\tTags: %s\n", jsonOutput));
   }
}
