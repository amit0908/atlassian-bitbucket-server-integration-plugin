package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Action;
import hudson.model.Item;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkins.ui.icon.IconType;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;

public class BitbucketExternalLink implements Action, IconSpec {

    private final Item owner;
    private final String displayName;
    private final String url;
    private final String iconName;

    static {
        IconSet.icons.addIcon(
                new Icon(
                        "icon-bitbucket-logo icon-md",
                        "atlassian-bitbucket-server-integration/images/24x24/bitbucket.png",
                        Icon.ICON_MEDIUM_STYLE,
                        IconType.PLUGIN));
    }

    private BitbucketExternalLink(Item owner, String displayName, String url, String iconClassName) {
        this.owner = owner;
        this.displayName = displayName;
        this.url = url;
        this.iconName = iconClassName;
    }

    public static BitbucketExternalLink createDashboardLink(String url, Item owner) {
        String displayName = "Go to Bitbucket";
        String iconName = "icon-bitbucket-logo";
        return new BitbucketExternalLink(owner, displayName, url, iconName);
    }

    @Override
    public String getIconClassName() {
        return iconName;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        if (!owner.hasPermission(Item.CONFIGURE)) {
            return null;
        }

        JellyContext ctx = new JellyContext();
        ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
        return IconSet.icons.getIconByClassSpec(iconName + " icon-md").getQualifiedUrl(ctx);
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return url;
    }
}
