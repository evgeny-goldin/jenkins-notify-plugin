package org.jenkinsci.plugins.notify;

import static org.apache.http.util.Args.notBlank;
import static org.apache.http.util.Args.notNull;
import com.google.common.io.Resources;
import groovy.json.JsonSlurper;
import groovy.text.SimpleTemplateEngine;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.Asserts;
import org.apache.http.util.EntityUtils;
import org.apache.http.util.TextUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class NotifyRecorder extends Recorder
{
    private static final int        CONNECT_TIMEOUT         = 10000;
    private static final int        CONNECT_REQUEST_TIMEOUT = 10000;
    private static final int        SOCKET_TIMEOUT          = 10000;
    private static final HttpClient HTTP_CLIENT             = buildHttpClient();
    private static final String     JSON_FUNCTION           = loadResource( "/json.groovy" );
    private static final String     DEFAULT_TEMPLATE        = loadResource( "/default-template.json"  );
    private static final String     LINE                    = "\n---------------\n";

    @Nullable public  final     String        notifyUrl;
    @Nonnull  public  final     String        notifyTemplate;
    @Nonnull  private transient BuildListener listener;


    private static HttpClient buildHttpClient () {
        RequestConfig requestConfig = RequestConfig.custom().
                                      setConnectTimeout( CONNECT_TIMEOUT ).
                                      setSocketTimeout( SOCKET_TIMEOUT ).
                                      setConnectionRequestTimeout( CONNECT_REQUEST_TIMEOUT ).
                                      build();

        return HttpClients.custom().setDefaultRequestConfig( requestConfig ).
               build();
    }


    private static String loadResource ( String resourcePath ) {
        try
        {
            URL resourceUrl = notNull( NotifyRecorder.class.getResource( notBlank( resourcePath, "Resource path" )), "Resource URL" );
            return notBlank( Resources.toString( resourceUrl, StandardCharsets.UTF_8 ),
                             String.format( "Resource '%s'", resourcePath ));
        }
        catch ( Exception e )
        {
            throw new RuntimeException( String.format( "Failed to load resource '%s': %s", resourcePath, e ),
                                        e );
        }
    }


    @SuppressWarnings({ "ParameterHidesMemberVariable", "SuppressionAnnotation" })
    @DataBoundConstructor
    public NotifyRecorder ( String notifyUrl, String notifyTemplate ) {
        this.notifyUrl      = TextUtils.isBlank( notifyUrl ) ? null : notifyUrl.trim();
        this.notifyTemplate = ( TextUtils.isBlank( notifyTemplate ) ? DEFAULT_TEMPLATE : notifyTemplate ).trim();
    }


    public BuildStepMonitor getRequiredMonitorService ()
    {
        return BuildStepMonitor.NONE;
    }


    /**
     * Publishes JSON payload to notify URL.
     */
    @SuppressWarnings({ "MethodWithMultipleReturnPoints", "SuppressionAnnotation" })
    @Override
    public boolean perform ( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener )
        throws InterruptedException, IOException
    {
        this.listener              = notNull( listener, "Build listener" );
        boolean isIntermediateStep = build.getUrl().contains( "$" );
        boolean isSuccessResult    = build.getResult().isBetterOrEqualTo( Result.SUCCESS );

        if ( isIntermediateStep || TextUtils.isBlank ( notifyUrl ) || ( ! isSuccessResult )) { return true; }

        listener.getLogger().println( String.format( "Building notify JSON payload" ));
        String notifyJson = buildNotifyJson( build, build.getEnvironment( listener ));

        listener.getLogger().println( String.format( "Publishing notify JSON payload to %s", notifyUrl ));
        // noinspection ConstantConditions
        sendNotifyRequest( notifyUrl, notifyJson );
        return true;
    }


    private String buildNotifyJson( @Nonnull final AbstractBuild build,
                                    @Nonnull final Map<String,?> env )
    {
        Map<String,?> binding = new HashMap<String, Object>(){{
           put( "jenkins", notNull( Jenkins.getInstance(), "Jenkins instance" ));
           put( "build",   notNull( build, "Build instance" ));
           put( "env",     notNull( env, "Build environment" ));
        }};

        String json     = null;
        String template = "<%\n\n" + JSON_FUNCTION + "\n\n%>\n\n" +
                          notBlank( notifyTemplate, "Notify template" );

        try
        {
            json = notBlank( new SimpleTemplateEngine( getClass().getClassLoader()).
                             createTemplate( template ).
                             make( binding ).toString(), "Payload JSON" ).trim();

            Asserts.check(( json.startsWith( "{" ) && json.endsWith( "}" )) ||
                          ( json.startsWith( "[" ) && json.endsWith( "]" )),
                          "Illegal JSON content: should start and end with {} or []" );

            Asserts.notNull( new JsonSlurper().parseText( json ), "Parsed JSON" );
        }
        catch ( Exception e )
        {
            throwError(( json == null ?
                String.format( "Failed to parse Groovy template:%s%s%s",
                               LINE, template, LINE ) :
                String.format( "Failed to validate JSON payload (check with http://jsonlint.com/):%s%s%s",
                               LINE, json, LINE )), e );
        }

        return json;
    }


    private void sendNotifyRequest( @Nonnull String url, @Nonnull String json )
        throws IOException
    {
        try
        {
            HttpPost request = new HttpPost( notBlank( url, "Notify URL" ));
            request.setEntity( new StringEntity( notBlank( json, "Notify JSON" ),
                                                 ContentType.create( "application/json", Consts.UTF_8 )));
            HttpResponse response   = HTTP_CLIENT.execute( request );
            int          statusCode = response.getStatusLine().getStatusCode();

            Asserts.check( statusCode == 200, String.format( "status code is %s, expected 200", statusCode ));
            EntityUtils.consumeQuietly( notNull( response.getEntity(), "Response entity" ));
            request.releaseConnection();
        }
        catch ( Exception e )
        {
            throwError( String.format( "Failed to publish notify request to '%s', payload JSON was:%s%s%s",
                                       notifyUrl, LINE, json, LINE ), e );
        }
    }


    private void throwError( String message, Exception e ) {
        listener.error( "%s: %s", message, e );
        throw new RuntimeException( message, e );
    }


    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {
        @Override
        public String getDisplayName ()
        {
            return "Publish HTTP POST notification";
        }

        @Override
        public boolean isApplicable ( Class<? extends AbstractProject> jobType )
        {
            return true;
        }


        public String getDefaultNotifyTemplate()
        {
            return DEFAULT_TEMPLATE;
        }


        public FormValidation doCheckNotifyUrl( @QueryParameter String notifyUrl ) {

            if ( TextUtils.isBlank( notifyUrl )) {
                return FormValidation.ok();
            }

            try {
                URI urlObject = new URI(notifyUrl);
                String scheme = urlObject.getScheme();
                String host   = urlObject.getHost();

                if ( ! (( "http".equals( scheme )) || ( "https".equals( scheme )))) {
                    return FormValidation.error( "URL should start with 'http://' or 'https://'" );
                }

                if ( TextUtils.isBlank( host )) {
                    return FormValidation.error( "URL should contain a host" );
                }

            } catch ( Exception e ) {
                return FormValidation.error( e, "Invalid URL provided" );
            }

            return FormValidation.ok();
        }


        public FormValidation doCheckNotifyTemplate( @QueryParameter String notifyTemplate ) {
            return FormValidation.ok();
        }
    }
}
