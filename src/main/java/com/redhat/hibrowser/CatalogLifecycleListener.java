package com.redhat.hibrowser;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public final class CatalogLifecycleListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(CatalogLifecycleListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        try {
            CatalogService service = CatalogService.fromEnvironment();
            context.setAttribute(CatalogService.CONTEXT_ATTRIBUTE, service);
            service.start();
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Vulnerability refresh could not start", ex);
            context.setAttribute(CatalogService.CONTEXT_ATTRIBUTE, CatalogService.unavailable(ex.getMessage()));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        Object service = event.getServletContext().getAttribute(CatalogService.CONTEXT_ATTRIBUTE);
        if (service instanceof CatalogService catalogService) {
            catalogService.close();
        }
    }
}
