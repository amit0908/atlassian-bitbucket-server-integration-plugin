package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRefChangeType;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class BitbucketWebhookConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitbucketWebhookConsumer.class);

    @Inject
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;

    void process(RefsChangedWebhookEvent event) {
        LOGGER.debug("Received refs changed event" + event);
        if (eligibleRefs(event).isEmpty()) {
            LOGGER.debug("Skipping processing of refs changed event because no refs have been added or updated");
            return;
        }
        RefChangedDetails refChangedDetails = new RefChangedDetails(event);

        try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
            BitbucketWebhookTriggerRequest request =
                    BitbucketWebhookTriggerRequest.builder().actor(event.getActor()).build();

            Jenkins.get().getAllItems(ParameterizedJobMixIn.ParameterizedJob.class)
                    .stream()
                    .map(BitbucketWebhookConsumer::toTriggerDetails)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(triggerDetails -> hasMatchingRepository(refChangedDetails, triggerDetails.getJob()))
                    .forEach(triggerDetails -> {
                        LOGGER.debug("Triggering " + triggerDetails.getJob().getFullDisplayName());
                        triggerDetails.getTrigger().trigger(request);
                    });
        }
    }

    void process(MirrorSynchronizedWebhookEvent event) {
        LOGGER.debug("Received mirror synchronized event" + event);
    }

    private static Set<String> cloneLinks(RefsChangedWebhookEvent event) {
        return event.getRepository()
                .getCloneUrls()
                .stream()
                .map(BitbucketNamedLink::getHref)
                .collect(Collectors.toSet());
    }

    private boolean hasMatchingRepository(RefChangedDetails refChangedDetails, ParameterizedJobMixIn.ParameterizedJob job) {
        Collection<? extends SCM> scms = getScms(job);
        for (SCM scm : scms) {
            if (scm instanceof GitSCM) {
                return hasMatchingRepository(refChangedDetails, (GitSCM) scm);
            } else if (scm instanceof BitbucketSCM) {
                return hasMatchingRepository(refChangedDetails, (BitbucketSCM) scm);
            }
        }
        return false;
    }

    private boolean hasMatchingRepository(RefChangedDetails refChangedDetails,
                                          BitbucketSCM scm) {
        return bitbucketPluginConfiguration.getServerById(scm.getServerId())
                .map(serverConfig -> {
                    if (refChangedDetails.getRepository().getSelfLink().startsWith(serverConfig.getBaseUrl())) {
                        return scm.getRepositories().stream()
                                .anyMatch(scmRepo -> matchingRepo(refChangedDetails.getRepository(), scmRepo));
                    }
                    return false;
                }).orElse(false);
    }

    private static Set<String> eligibleRefs(RefsChangedWebhookEvent event) {
        return event.getChanges()
                .stream()
                .filter(refChange -> refChange.getType() != BitbucketRefChangeType.DELETE)
                .map(refChange -> refChange.getRef().getId())
                .collect(Collectors.toSet());
    }

    private static boolean hasMatchingRepository(RefChangedDetails refChangedDetails,
                                                 GitSCM scm) {
        return scm.getRepositories().stream()
                .anyMatch(scmRepo -> matchingRepo(refChangedDetails.getCloneLinks(), scmRepo));
    }

    private static boolean matchingRepo(BitbucketRepository repository, BitbucketSCMRepository scmRepo) {
        return scmRepo.getProjectKey().equals(repository.getProject().getKey())
                && scmRepo.getRepositorySlug().equals(repository.getSlug());
    }

    private static boolean matchingRepo(Set<String> cloneLinks, RemoteConfig repo) {
        return repo.getURIs().stream().anyMatch(uri -> cloneLinks.contains(uri.toString()));
    }

    private static Optional<TriggerDetails> toTriggerDetails(ParameterizedJobMixIn.ParameterizedJob job) {
        BitbucketWebhookTriggerImpl trigger = triggerFrom(job);
        if (trigger != null) {
            return Optional.of(new TriggerDetails(job, trigger));
        }
        return Optional.empty();
    }

    @Nullable
    private static BitbucketWebhookTriggerImpl triggerFrom(ParameterizedJobMixIn.ParameterizedJob job) {
        Map<TriggerDescriptor, Trigger<?>> triggers = job.getTriggers();
        for (Trigger candidate : triggers.values()) {
            if (candidate instanceof BitbucketWebhookTriggerImpl) {
                return (BitbucketWebhookTriggerImpl) candidate;
            }
        }
        return null;
    }

    private static Collection<? extends SCM> getScms(ParameterizedJobMixIn.ParameterizedJob job) {
        SCMTriggerItem triggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
        if (triggerItem != null) {
            return triggerItem.getSCMs();
        }
        return Collections.emptySet();
    }

    private static final class TriggerDetails {

        private final ParameterizedJobMixIn.ParameterizedJob job;
        private final BitbucketWebhookTrigger trigger;

        private TriggerDetails(ParameterizedJobMixIn.ParameterizedJob job, BitbucketWebhookTrigger trigger) {
            this.job = job;
            this.trigger = trigger;
        }

        public ParameterizedJobMixIn.ParameterizedJob getJob() {
            return job;
        }

        public BitbucketWebhookTrigger getTrigger() {
            return trigger;
        }
    }

    private static final class RefChangedDetails {

        private final Set<String> cloneLinks;
        private final BitbucketRepository repository;

        private RefChangedDetails(RefsChangedWebhookEvent event) {
            this.cloneLinks = cloneLinks(event);
            this.repository = event.getRepository();
        }

        public Set<String> getCloneLinks() {
            return cloneLinks;
        }

        public BitbucketRepository getRepository() {
            return repository;
        }
    }
}