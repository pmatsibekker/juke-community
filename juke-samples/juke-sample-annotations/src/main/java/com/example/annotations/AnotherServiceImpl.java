package com.example.annotations;

import org.springframework.stereotype.Service;

@Service
public class AnotherServiceImpl implements IAnotherService {

    @Override
    public String processRequest() {
        return "Request processed by AnotherServiceImpl";
    }
}
