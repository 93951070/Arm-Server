package armadillo.admin.filter;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class AdminAuthFilter implements Filter {
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/login", "/css/", "/js/", "/favicon.ico", "/error"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();
        String servletPath = path.substring(contextPath.length());
        for (String white : WHITE_LIST) {
            if (servletPath.startsWith(white) || servletPath.equals("/")) {
                chain.doFilter(request, response);
                return;
            }
        }
        Object adminUser = req.getSession(false) != null ? req.getSession().getAttribute("adminUser") : null;
        if (adminUser != null) {
            chain.doFilter(request, response);
            return;
        }
        resp.sendRedirect(contextPath + "/login");
    }
}
