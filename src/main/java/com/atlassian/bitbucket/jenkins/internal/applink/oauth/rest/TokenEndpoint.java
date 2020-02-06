package com.atlassian.bitbucket.jenkins.internal.applink.oauth.rest;

import hudson.model.InvisibleAction;
import org.json.JSONException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;

public class TokenEndpoint extends InvisibleAction {

    private RequestTokenRestEndpoint requestTokenRestEndpoint = new RequestTokenRestEndpoint();
    private AccessTokenRestEndpoint accessTokenRestEndpoint = new AccessTokenRestEndpoint();
    private AuthorizeServlet authorizeServlet = new AuthorizeServlet();

    @RequirePOST
    @WebMethod(name = "access-token")
    public void doAccessToken(StaplerRequest request,
                              StaplerResponse response) throws ServletException, IOException {
        accessTokenRestEndpoint.handleAccessToken(request, response);
    }

    @RequirePOST
    @WebMethod(name = "request-token")
    public void doRequestToken(StaplerRequest req,
                               StaplerResponse resp) throws ServletException, IOException {
        requestTokenRestEndpoint.handleRequestToken(req, resp);
    }

    @WebMethod(name = "authorize")
    public void doAuthorizeToken(StaplerRequest req,
                                 StaplerResponse resp) throws ServletException, JSONException, IOException {
        authorizeServlet.authorize(req, resp);
    }
}
