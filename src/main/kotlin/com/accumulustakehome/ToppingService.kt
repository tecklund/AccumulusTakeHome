package com.accumulustakehome

import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.boot.json.JsonParserFactory
import org.slf4j.LoggerFactory

data class ToppingWithCount(val id: Int, val topping:String, val customerCount:Int)
data class CustomerPrefrences(val id: Int, val email:String, val toppings:List<String>)
data class Topping(val id:Int, val topping:String)

/**
 * ToppingService gives us access to the peristance layer for our topping info
 */
@Service
class ToppingService(val db:JdbcTemplate){
  private val logger = LoggerFactory.getLogger(javaClass)
  val springParser = JsonParserFactory.getJsonParser()

  /**
   * Returns all the toppings that have been entered, along with the count of how many people wanted each one
   */
  public fun getToppings():List<ToppingWithCount> =
    db.query("""
            select t.id, t.topping, count(distinct c.id) as count from pizza.customer_topping ct
            join pizza.topping t on t.id = ct.topping_id
            join pizza.customer c on c.id = ct.customer_id
            group by t.id, t.topping
            """, { response, _ -> ToppingWithCount( response.getInt("id"),
                                                    response.getString("topping"),
                                                    response.getInt("count"))})
    

  /**
   * Upserts a customer, and overwrites thier topping choices
   */
  @Transactional
  public fun registerCustomer(email:String, toppings:List<String>):Unit {
    /*
      Upserts a customer and returns the id - there are a couple ways to do this,
      however the CTE/union method avoids unnecessary writes and saves us from
      possible concurrency problems
    */
    val customerid = db.query("""
              WITH possibleinsert AS(
                INSERT INTO pizza.customer (email) VALUES (?) 
                ON CONFLICT DO NOTHING RETURNING id
              )
              SELECT id FROM possibleinsert
              UNION
              SELECT id FROM pizza.customer WHERE email = ?
    """, {result, _ -> result.getInt("id")}, email, email)[0]
    
    // remove old topping preferences - these get auto-vaccumed by pg
    db.update("delete from pizza.customer_topping where customer_id = ?", customerid)
    // iterate toppings
    toppings.forEach{
      // upsert topping, we don't care about getting the id back (make it lowercase)
      db.update("insert into pizza.topping (topping) values (?) on conflict do nothing", it.lowercase())
      // insert customer-topping link
      db.update("insert into pizza.customer_topping (customer_id, topping_id) select ?, id from pizza.topping where topping = ?", customerid, it.lowercase())
    }
  }

  /**
   * Get info for a specific topping, and insert it if it didn't exist before
   */
  public fun getToppingInfo(topping:String) = 
    db.query("""
      WITH possibleinsert AS(
          INSERT INTO pizza.topping (topping) VALUES (?) 
          ON CONFLICT DO NOTHING
          RETURNING id, topping
      )
      SELECT id, topping FROM possibleinsert
      UNION
      SELECT id, topping FROM pizza.topping WHERE topping = ?
    """, {result, _ -> Topping(result.getInt("id"), result.getString("topping"))}, topping.lowercase(), topping.lowercase())


  /**
   * Get all customers and which toppings they liked
   */
  @Suppress("UNCHECKED_CAST") //its a list of strings for real
  public fun getToppingsByCustomer() = 
    db.query("""
      select c.id, c.email, json_agg(t.topping) toppings from pizza.customer c
      join pizza.customer_topping ct on c.id = ct.customer_id
      join pizza.topping t on t.id = ct.topping_id
      group by c.email, c.id
    """, {response, _ -> CustomerPrefrences(
                                            response.getInt("id"),
                                            response.getString("email"),
                                            springParser.parseList(response.getString("toppings")) as List<String> )})
}

