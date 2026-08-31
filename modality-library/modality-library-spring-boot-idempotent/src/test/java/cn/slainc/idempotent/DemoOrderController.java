package cn.slainc.idempotent;

import cn.slainc.core.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
class DemoOrderController {

    private final IdempotentTestApplication.OrderService orderService;

    private final IdempotentTestApplication.TokenOrderService tokenOrderService;

    DemoOrderController(IdempotentTestApplication.OrderService orderService,
                        IdempotentTestApplication.TokenOrderService tokenOrderService) {
        this.orderService = orderService;
        this.tokenOrderService = tokenOrderService;
    }

    @PostMapping("/create")
    public Result<String> create(@RequestBody IdempotentTestApplication.OrderRequest request) {
        return orderService.create(request);
    }

    @PostMapping("/create-slow")
    public Result<String> createSlow(@RequestBody IdempotentTestApplication.OrderRequest request) {
        return orderService.createSlow(request);
    }

    @PostMapping("/submit-token")
    public Result<String> submitToken(@RequestBody IdempotentTestApplication.TokenSubmitRequest request) {
        return tokenOrderService.submitByToken(request.token(), request.orderNo());
    }
}
