package com.manolo.lor.spanner;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LorEngineController {

    @Autowired
    LorEngineService engine;

    @PostMapping(value = "/api/run", produces = "application/json")
	public LorEngineResponse runPost(@RequestBody Map<String, Object> payload) {
        LorEngineRequest req = new LorEngineRequest(payload);
        return engine.run(req);
	}
}
