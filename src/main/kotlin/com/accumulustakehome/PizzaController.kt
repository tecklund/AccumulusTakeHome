package com.accumulustakehome

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory

/**
 * A singleton that holds constants
 */
object Consts {
  //RFC5322 regex for email validation - there are other ones, but it seems ok for this test program
  val emailregex = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$".toRegex()
}

/**
 * PizzaController is an API that allows:
 * 1) Customers to register thier email address and topping preferences
 * 2) UI devs to see all preferred toppings, and a count of how many people like each one
 * 3) UI devs to see which people like which toppings
 */
@RestController
public class PizzaController(val service:ToppingService){
  private val logger = LoggerFactory.getLogger(javaClass)

  /**
   * Returns all of the preferred toppings, along with how many people chose each one
   */
  @GetMapping("/")
  public fun index():List<ToppingWithCount> = service.getToppings()

  /**
   * Returns all customers and which toppings they preferred
   */
  @GetMapping("/toppingsByCustomer")
  public fun customers():List<CustomerPrefrences> = service.getToppingsByCustomer()

  /**
   * Puts a customer and thier preferred toppings
   * 
   * This is an HTTP Put because it's idempotent - old values are overwritten by new ones
   * 
   * @param email Path variable that holds the email address (fine if we're just doing REST calls)
   * @param toppings Json array that holds topping preferences
   * @return ok when successful, 400 when email is not valid
   */
  @PutMapping("/register/{email}")
  public fun register(@PathVariable email:String, @RequestBody toppings:List<String>):ResponseEntity<String> {
    // gate on malformed email address
    if(!Consts.emailregex.matches(email)){
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Email Address")
    }
    service.registerCustomer(email, toppings)
    return ResponseEntity.ok("ok")
  }

  /**
   * Returns my favorite topping!
   */
  @GetMapping("/timsfavorite")
  public fun timsfav() = service.getToppingInfo("mushrooms")

  /**
   * Returns pong, useful for checking that the system has started
   */
  @GetMapping("/ping")
  public fun ping() = "pong"

}