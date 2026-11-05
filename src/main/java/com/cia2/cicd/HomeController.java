package com.cia2.cicd;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    private final AppInfoService info;

    public HomeController(AppInfoService info) {
        this.info = info;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("info", info);
        return "index";
    }

    @GetMapping("/api/info")
    @ResponseBody
    public Map<String, String> apiInfo() {
        return info.asMap();
    }

    @GetMapping("/api/greet")
    @ResponseBody
    public Map<String, String> greet(@RequestParam(required = false) String name) {
        return Map.of("greeting", info.greet(name));
    }
}
