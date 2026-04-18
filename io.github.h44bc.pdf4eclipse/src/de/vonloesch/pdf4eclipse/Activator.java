/*******************************************************************************
 * Copyright (c) 2011 Boris von Loesch.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Boris von Loesch - initial API and implementation
 ******************************************************************************/
package de.vonloesch.pdf4eclipse;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;

import de.vonloesch.pdf4eclipse.editors.handlers.ToggleLinkHighlightHandler;
import de.vonloesch.pdf4eclipse.preferences.PreferenceConstants;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "io.github.h44bc.pdf4eclipse"; //$NON-NLS-1$
	private static final String LEGACY_PLUGIN_ID = "de.vonloesch.pdf4Eclipse"; //$NON-NLS-1$
	private static final String LEGACY_LINK_HIGHLIGHT_ID = "de.vonloesch.pdf4eclipse.preferences.linkHighlight"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		migrateLegacyPreferences();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	/**
	 * Writes a message to the log.
	 * @param message
	 * @param severity One of <tt>Status.INFO, Status.WARNING, Status.ERROR</tt>  
	 */
	public static void log(String message, int severity) {
		if (plugin != null) {
			plugin.getLog().log(new Status(severity, PLUGIN_ID, message));
		}
	}
	
	/**
	 * Writes an error message to the log.
	 * @param message
	 * @param throwable
	 */
	public static void log(String message, Throwable throwable) {
		if (plugin != null) {
			plugin.getLog().log(new Status(Status.ERROR, PLUGIN_ID, message, throwable));
		}
	}

	private void migrateLegacyPreferences() {
		IEclipsePreferences legacyNode = InstanceScope.INSTANCE.getNode(LEGACY_PLUGIN_ID);
		IEclipsePreferences currentNode = InstanceScope.INSTANCE.getNode(PLUGIN_ID);

		copyIfMissing(legacyNode, currentNode, PreferenceConstants.PSEUDO_CONTINUOUS_SCROLLING);
		copyIfMissing(legacyNode, currentNode, PreferenceConstants.PDF_RENDERER);
		copyIfMissing(legacyNode, currentNode, ToggleLinkHighlightHandler.PREF_LINKHIGHTLIGHT_ID,
				LEGACY_LINK_HIGHLIGHT_ID);

		try {
			currentNode.flush();
		} catch (BackingStoreException e) {
			log("Failed to flush migrated preference values.", e);
		}
	}

	private static void copyIfMissing(IEclipsePreferences source, IEclipsePreferences target, String key) {
		copyIfMissing(source, target, key, key);
	}

	private static void copyIfMissing(IEclipsePreferences source, IEclipsePreferences target, String targetKey,
			String sourceKey) {
		String existing = target.get(targetKey, null);
		if (existing != null) {
			return;
		}
		String legacyValue = source.get(sourceKey, null);
		if (legacyValue != null) {
			target.put(targetKey, legacyValue);
		}
	}
}
