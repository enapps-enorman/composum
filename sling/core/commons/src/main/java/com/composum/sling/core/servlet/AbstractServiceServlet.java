package com.composum.sling.core.servlet;

import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.mapping.MappingRules;
import com.composum.sling.core.util.JsonUtil;
import com.composum.sling.core.util.LoggerFormat;
import com.composum.sling.core.util.ResponseUtil;
import com.composum.sling.cpnl.CpnlElFunctions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A basic class for all '/bin/{service}/path/to/resource' servlets.
 */
@Component(componentAbstract = true)
public abstract class AbstractServiceServlet extends SlingAllMethodsServlet {

    public static final String PARAM_FILE = "file";
    public static final String PARAM_CMD = "cmd";
    public static final String PARAM_FILTER = "filter";
    public static final String PARAM_ID = "id";
    public static final String PARAM_INDEX = "index";
    public static final String PARAM_JCR_CONTENT = "jcrContent";
    public static final String PARAM_LABEL = "label";
    public static final String PARAM_MIME_TYPE = "mimeType";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_PATH = "path";
    public static final String PARAM_BEFORE = "before";
    public static final String PARAM_QUERY = "query";
    public static final String PARAM_RESOURCE_TYPE = "resourceType";
    public static final String PARAM_TITLE = "title";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_URL = "url";
    public static final String PARAM_VALUE = "value";
    public static final String PARAM_VERSION = "version";

    public static final String DATE_FORMAT = "yyyy-MM-DD HH:mm:ss";

    protected abstract boolean isEnabled();

    protected boolean isEnabled(SlingHttpServletResponse response) {
        boolean enabled = isEnabled();
        if (!enabled) {
            response.setContentLength(0);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return enabled;
    }

    //
    // Servlet method implementation using ServletOperationSet
    //

    /**
     * Each servlet implementation must provide access to its operation set for request delegation.
     */
    protected abstract ServletOperationSet getOperations();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        if (isEnabled(response)) {
            setNoCacheHeaders(response);
            getOperations().doGet(request, response);
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        if (isEnabled(response)) {
            setNoCacheHeaders(response);
            getOperations().doPost(request, response);
        }
    }

    @Override
    protected void doPut(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        if (isEnabled(response)) {
            setNoCacheHeaders(response);
            getOperations().doPut(request, response);
        }
    }

    @Override
    protected void doDelete(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        if (isEnabled(response)) {
            setNoCacheHeaders(response);
            getOperations().doDelete(request, response);
        }
    }

    //
    // HTTP control methods
    //

    public static void setNoCacheHeaders(SlingHttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    //
    // Validation handling
    //

    public class Validation {

        protected List<String> messages = new ArrayList<>();

        public void addMessage(SlingHttpServletRequest request, String message, Object value) {
            message = CpnlElFunctions.i18n(request, message);
            message = LoggerFormat.format(message, value);
            messages.add(message);
        }

        @Nullable
        public String getRequiredParameter(SlingHttpServletRequest request, String paramName,
                                           Pattern pattern, String errorMessage) {
            final RequestParameter requestParameter = request.getRequestParameter(paramName);
            String value = null;
            if (requestParameter != null) {
                value = requestParameter.getString();
                if (pattern == null || pattern.matcher(value).matches()) {
                    return value;
                }
            }
            addMessage(request, errorMessage, value);
            return null;
        }

        @Nullable
        public List<String> getRequiredParameters(SlingHttpServletRequest request, String paramName,
                                                  Pattern pattern, String errorMessage) {
            final RequestParameter[] requestParameters = request.getRequestParameters(paramName);
            if (requestParameters == null || requestParameters.length < 1) {
                addMessage(request, errorMessage, null);
                return null;
            }
            List<String> values = new ArrayList<>();
            for (RequestParameter parameter : requestParameters) {
                String value = parameter.getString();
                if (pattern != null && !pattern.matcher(value).matches()) {
                    addMessage(request, errorMessage, value);
                }
                values.add(value);
            }
            return values;
        }

        public boolean sendError(SlingHttpServletResponse response)
                throws IOException {
            if (messages.size() > 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, messages.get(0));
                return true;
            }
            return false;
        }
    }

    //
    // service resource retrieval
    //

    /**
     * Retrieves the resource using the suffix from the request.
     *
     * @param request the sling request with the resource path in the suffix
     * @return the resource (NOT <code>null</code>; returns a handle with an invalid resource if not resolvable)
     */
    public static ResourceHandle getResource(SlingHttpServletRequest request) {
        ResourceResolver resolver = request.getResourceResolver();
        String path = getPath(request);
        return ResourceHandle.use(resolver.resolve(path));
    }

    public static String getPath(SlingHttpServletRequest request) {
        RequestPathInfo reqPathInfo = request.getRequestPathInfo();
        String path = reqPathInfo.getSuffix();
        if (StringUtils.isBlank(path)) {
            path = request.getParameter(PARAM_PATH);
        }
        return path;
    }

    //
    // default responses...
    //

    protected void jsonAnswerItemExists(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        JsonWriter jsonWriter = ResponseUtil.getJsonWriter(response);
        jsonWriter.beginObject();
        jsonWriter.name("success").value(false);
        jsonWriter.name("messages").beginArray();
        jsonWriter.beginObject();
        jsonWriter.name("level").value("warn");
        jsonWriter.name("text").value(CpnlElFunctions.i18n(request,
                "An element with the same name exists already - use a different name!"));
        jsonWriter.endObject();
        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    //
    // JSON answer helpers
    //

    public static void jsonValue(JsonWriter writer, Object value) throws IOException {
        if (value instanceof String) {
            writer.value((String) value);
        } else if (value instanceof Map) {
            Map map = (Map) value;
            writer.beginObject();
            for (Object key : map.keySet()) {
                writer.name(key.toString());
                jsonValue(writer, map.get(key));
            }
            writer.endObject();
        } else if (value instanceof Object[]) {
            writer.beginArray();
            for (Object v : (Object[]) value) {
                jsonValue(writer, v);
            }
            writer.endArray();
        } else if (value instanceof Iterable) {
            writer.beginArray();
            for (Object v : (Iterable) value) {
                jsonValue(writer, v);
            }
            writer.endArray();
        } else if (value instanceof Iterator) {
            Iterator iterator = (Iterator) value;
            writer.beginArray();
            while (iterator.hasNext()) {
                jsonValue(writer, iterator.next());
            }
            writer.endArray();
        } else if (value instanceof Calendar) {
            writer.value(new SimpleDateFormat(DATE_FORMAT).format(((Calendar) value).getTime()));
        } else {
            writer.value(value != null ? value.toString() : null);
        }
    }

    //
    // JSON parameters parsing
    //

    public static <T> T getJsonObject(SlingHttpServletRequest request, Class<T> type)
            throws IOException {
        InputStream inputStream = request.getInputStream();
        Reader inputReader = new InputStreamReader(inputStream, MappingRules.CHARSET.name());
        // parse JSON input into a POJO of the requested type
        Gson gson = JsonUtil.GSON_BUILDER.create();
        return gson.fromJson(inputReader, type);
    }

    public static <T> T getJsonObject(SlingHttpServletRequest request, Class<T> type,
                                      InstanceCreator<T> instanceCreator)
            throws IOException {
        InputStream inputStream = request.getInputStream();
        Reader inputReader = new InputStreamReader(inputStream, MappingRules.CHARSET.name());
        // parse JSON input into a POJO of the requested type
        Gson gson = new GsonBuilder().registerTypeAdapter(type, instanceCreator).create();
        return gson.fromJson(inputReader, type);
    }

    public static <T> T getJsonObject(String input, Class<T> type) {
        Reader inputReader = new StringReader(input);
        // parse JSON input into a POJO of the requested type
        Gson gson = JsonUtil.GSON_BUILDER.create();
        return gson.fromJson(inputReader, type);
    }
}
