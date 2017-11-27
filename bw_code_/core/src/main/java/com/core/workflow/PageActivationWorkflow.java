package com.core.workflow;

import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

//import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;

@Component
@Service
@Properties({ @Property(name = "BWI Sample Project", value = "BWI Activation Workflow"),
		@Property(label = "Workflow Label", name = "process.label", value = "BWI Activation Workflow", description = "This triggers when BWPROP page property is modified or on new page creation") })
public class PageActivationWorkflow implements WorkflowProcess {

	private static final Logger LOG = LoggerFactory.getLogger(PageActivationWorkflow.class);

	@Reference
	private Replicator replicator;

	@Reference
	private ResourceResolverFactory resolverFactory;

	/**
	 * Workflow will check for BWPROP keyword. If BWPROP property has some value, Page will be activated
	 * otherwise workflow will be terminated and page will not be activated.
	 * 
	 */
	@Override
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
			throws WorkflowException {
		String path = workItem.getWorkflowData().getPayload().toString();
		ResourceResolver resourceResolver = null;
		if (!"".equalsIgnoreCase(path)) {
			Session session = workflowSession.adaptTo(Session.class);
			try {
				resourceResolver = getResourceResolver(session);
				if (null != resourceResolver) {

					Resource resource = resourceResolver.getResource(path);
					if (null != resource) {
						getBwpropProperty(workItem, workflowSession, session, path, resource);

					}
				}
				session.save();
				session.refresh(true);
			} catch (RepositoryException e) {
				LOG.error("Repository Exception:" + e.getMessage());
			} catch (LoginException e) {
				LOG.error("Login Exception:" + e.getMessage());
			}
		}
	}

	private void getBwpropProperty(WorkItem workItem, WorkflowSession workflowSession, Session session, String path,
			Resource resource) {
		Node node = resource.adaptTo(Node.class);
		if (null != node) {
			try {
				if (node.hasProperty("BWPROP")) {
					Value bwPropPropertyValue = node.getProperty("BWPROP").getValue();
					String bwPropProperty = bwPropPropertyValue.toString();
					replicatePage(workItem, workflowSession, session, path, bwPropProperty);
				}
			} catch (RepositoryException e) {
				LOG.error("Repository Exception:" + e.getMessage());
			}
		}
	}

	private void replicatePage(WorkItem workItem, WorkflowSession workflowSession, Session session, String path,
			String activationProperty) {
		try {
			if (null != activationProperty && activationProperty.length() > 0) {
				replicator.replicate(session, ReplicationActionType.ACTIVATE, path);
				session.save();
				session.refresh(true);

			} else {
				terminateWorkflow(workflowSession, workItem);
			}

		} catch (ReplicationException e) {
			LOG.error("Replication Exception:" + e.getMessage());
		} catch (WorkflowException e) {
			LOG.error("Workflow Exception:" + e.getMessage());
		} catch (RepositoryException e) {
			LOG.error("Repository Exception:" + e.getMessage());
		}

	}

	public static void terminateWorkflow(WorkflowSession workflowSession, WorkItem workItem) throws WorkflowException {
		if (workItem.getWorkflow().isActive()) {
			workflowSession.terminateWorkflow(workItem.getWorkflow());
		}
	}

	public ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resolverFactory.getResourceResolver(
				Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session));
	}

}
