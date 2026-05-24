/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example;


import org.springframework.web.bind.annotation.RequestParam;



/**
 *
 *
 */

public interface IGreetingController {

	public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name);
}
