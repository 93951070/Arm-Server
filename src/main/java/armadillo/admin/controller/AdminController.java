package armadillo.admin.controller;

import armadillo.admin.config.ArmadilloAdminProperties;
import armadillo.admin.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Map;

@Controller
public class AdminController {

    @Autowired
    private AdminService adminService;
    @Autowired
    private ArmadilloAdminProperties adminProperties;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        if (adminProperties.getUsername().equals(username) && adminProperties.getPassword().equals(password)) {
            session.setAttribute("adminUser", username);
            return "redirect:/dashboard";
        }
        model.addAttribute("error", "用户名或密码错误");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        Map<String, Object> stats = adminService.getDashboardStats();
        model.addAllAttributes(stats);
        model.addAttribute("currentPage", "dashboard");
        return "dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Model model) {
        Map<String, Object> data = adminService.getUsers(page, size);
        model.addAllAttributes(data);
        model.addAttribute("currentPage", "users");
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        long total = (long) data.getOrDefault("total", 0L);
        int totalPages = (int) Math.ceil((double) total / size);
        model.addAttribute("totalPages", totalPages);
        return "users";
    }

    @PostMapping("/users/ban")
    public String banUser(@RequestParam int userId) {
        adminService.banUser(userId);
        return "redirect:/users";
    }

    @PostMapping("/users/unban")
    public String unbanUser(@RequestParam int userId, @RequestParam(defaultValue = "30") int days) {
        adminService.unbanUser(userId, days);
        return "redirect:/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam int userId) {
        adminService.deleteUser(userId);
        return "redirect:/users";
    }

    @GetMapping("/cards")
    public String cards(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Model model) {
        Map<String, Object> data = adminService.getCards(page, size);
        model.addAllAttributes(data);
        model.addAttribute("currentPage", "cards");
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        long total = (long) data.getOrDefault("total", 0L);
        int totalPages = (int) Math.ceil((double) total / size);
        model.addAttribute("totalPages", totalPages);
        return "cards";
    }

    @PostMapping("/cards/create")
    public String createCards(@RequestParam int type, @RequestParam int count, @RequestParam int value, Model model) {
        java.util.List<String> cardKeys = adminService.createCards(type, count, value);
        model.addAttribute("newCards", cardKeys);
        model.addAttribute("success", "成功生成 " + cardKeys.size() + " 张卡密");
        Map<String, Object> data = adminService.getCards(0, 20);
        model.addAllAttributes(data);
        model.addAttribute("currentPage", "cards");
        return "cards";
    }

    @PostMapping("/cards/delete")
    public String deleteCard(@RequestParam int cardId) {
        adminService.deleteCard(cardId);
        return "redirect:/cards";
    }

    @GetMapping("/notices")
    public String notices(Model model) {
        Map<String, Object> data = adminService.getAllNotices();
        model.addAllAttributes(data);
        model.addAttribute("currentPage", "notices");
        return "notices";
    }

    @PostMapping("/notices/create")
    public String createNotice(@RequestParam String title, @RequestParam String msg) {
        adminService.addNotice(title, msg);
        return "redirect:/notices";
    }

    @PostMapping("/notices/delete")
    public String deleteNotice(@RequestParam int id) {
        adminService.deleteNotice(id);
        return "redirect:/notices";
    }

    @GetMapping("/versions")
    public String versions(Model model) {
        Map<String, Object> data = adminService.getAllVersions();
        model.addAllAttributes(data);
        model.addAttribute("currentPage", "versions");
        return "versions";
    }

    @PostMapping("/versions/create")
    public String createVersion(@RequestParam int versionCode, @RequestParam String versionName, @RequestParam(defaultValue = "false") boolean forceUpdate, @RequestParam String msg) {
        adminService.addVersion(versionCode, versionName, forceUpdate, msg);
        return "redirect:/versions";
    }

    @PostMapping("/versions/delete")
    public String deleteVersion(@RequestParam int id) {
        adminService.deleteVersion(id);
        return "redirect:/versions";
    }

    @PostMapping("/cache/refresh")
    public String refreshCache() {
        adminService.refreshCache();
        return "redirect:/dashboard";
    }
}
