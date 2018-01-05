package com.developersam.web.control.scheduler;

import com.developersam.web.model.scheduler.Scheduler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A servlet that loads list of scheduler items onto /apps/scheduler/.
 */
@WebServlet(name = "LoadItemsServlet", value = "/apps/scheduler/")
public class LoadItemsServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("schedulerItems",
                Scheduler.INSTANCE.getAllSchedulerItems());
        req.getRequestDispatcher("/apps/scheduler/index.jsp")
                .forward(req, resp);
    }
    
}