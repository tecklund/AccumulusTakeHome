package com.accumulustakehome

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.jdbc.core.JdbcTemplate
import org.hamcrest.CoreMatchers.`is` as Is
import org.hamcrest.MatcherAssert.assertThat
import org.springframework.web.reactive.function.client.WebClientResponseException

// for passing a type to a deserializer
inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
// call get on a uri and parse the result into a type T
inline fun <reified T> get(client:WebClient, uri:String) = client.get().uri(uri).retrieve().bodyToMono(typeReference<T>()).block()
// call put on a uri - just put the return val into a string
fun put(client:WebClient, uri:String, body:Any) = client.put().uri(uri).bodyValue(body).retrieve().bodyToMono(String::class.java).block()

/**
 * These are integration tests - they validate that the system works all
 * the way through, from the HTTP call to the DB and back again
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class PizzaControllerTest {

	private val logger = LoggerFactory.getLogger(javaClass)

	// needs to be an old school java Integer bc that's what LocalServerPort needs
	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
	@LocalServerPort
  lateinit var port : Integer

	lateinit var client:WebClient

	@Autowired
	lateinit var db:JdbcTemplate

	@BeforeAll
	fun init() {
		client = WebClient.create("http://localhost:"+port)
	}

	@BeforeEach
	fun cleardb() {
		//its cool to delete stuff, its local and we will never connect to prod
		db.update("delete from pizza.customer")
		db.update("delete from pizza.topping")
	}

	fun basicTestData(){
		val customerIds = db.query("insert into pizza.customer (email) values ('foo@gmail.com'), ('bar@gmail.com') returning id") {res, _ -> res.getInt("id")}
		val toppingIds = db.query("insert into pizza.topping (topping) values ('cheese'), ('pepperoni') returning id") {res, _ -> res.getInt("id")}
		db.update("insert into pizza.customer_topping (customer_id, topping_id) values (?,?), (?,?), (?,?)", 
							customerIds[0], toppingIds[0], //foo, cheese
							customerIds[0], toppingIds[1], //foo, pepperoni
							customerIds[1], toppingIds[0]  //bar, cheese
							)
	}

	@Test
	fun shouldReturnExpectedToppingsWithExpectedCounts() {
		// set up fixture data
		basicTestData()
		
		// call the index endpoint
		val resp = get<List<ToppingWithCount>>(client, "/api/") ?: listOf()

		// pull out the expected values
		val cheese:List<ToppingWithCount> = resp.filter {	item:ToppingWithCount -> 
																											item.topping == "cheese" &&
																											item.customerCount == 2}
		val pepperoni:List<ToppingWithCount> = resp.filter{	item:ToppingWithCount ->
																												item.topping == "pepperoni" &&
																												item.customerCount == 1}
		assertThat(cheese.size, Is(1))
		assertThat(pepperoni.size, Is(1))
	}

	@Test
	fun shouldGetToppingsByCustomer() {
		// set up fixture data
		basicTestData()

		//call the toppings by customer endpoint
		val resp = get<List<CustomerPrefrences>>(client, "/api/toppingsByCustomer") ?: listOf()

		// extract expected values
		val foo:List<CustomerPrefrences> = resp.filter {item:CustomerPrefrences -> 
																										item.email == "foo@gmail.com" &&
																										item.toppings.size == 2 &&
																										item.toppings.containsAll(listOf("cheese", "pepperoni"))}
		val bar:List<CustomerPrefrences> = resp.filter {item:CustomerPrefrences ->
																										item.email == "bar@gmail.com" &&
																										item.toppings.size == 1 &&
																										item.toppings.containsAll(listOf("cheese"))}
		assertThat(foo.size, Is(1))
		assertThat(bar.size, Is(1))
	}

	@Test
	fun shouldAddNewCustomersWithValidEmails(){
		// set up fixture data
		basicTestData()

		// register someone new
		val resp = put(client, "/api/register/baz@gmail.com", listOf("Cheese", "Sausage")) ?: ""
		assertThat(resp, Is("ok"))
		val toppings = get<List<CustomerPrefrences>>(client, "/api/toppingsByCustomer") ?: listOf()
		val baz:List<CustomerPrefrences> = toppings.filter {item:CustomerPrefrences ->
																												item.email == "baz@gmail.com" &&
																												item.toppings.size == 2 &&
																												// should be case insensative
																												item.toppings.containsAll(listOf("cheese", "sausage"))}
		assertThat(baz.size, Is(1))																		
	}

	@Test
	fun shouldNotAddNewCustomerWithInvalidEmail(){
		// this should throw a 400 since the email is bad
		assertThrows(WebClientResponseException.BadRequest::class.java, { put(client, "/api/register/bazgmail.com", listOf("Cheese", "Sausage")) })
		// check that we didn't insert a bad email
		val toppings = get<List<CustomerPrefrences>>(client, "/api/toppingsByCustomer") ?: listOf()
		val baz:List<CustomerPrefrences> = toppings.filter {item:CustomerPrefrences ->
																												item.email == "bazgmail.com" &&
																												item.toppings.size == 2 &&
																												// should be case insensative
																												item.toppings.containsAll(listOf("cheese", "sausage"))}
		assertThat(baz.size, Is(0))	
	}

	@Test
	fun shouldReplaceOldPreferencesOnRegistration(){
		// set up fixture data
		basicTestData()

		// register someone again (foo had cheese and pepperoni)
		val resp = put(client, "/api/register/foo@gmail.com", listOf("Sausage")) ?: ""
		assertThat(resp, Is("ok"))
		val toppings = get<List<CustomerPrefrences>>(client, "/api/toppingsByCustomer") ?: listOf()
		val foo:List<CustomerPrefrences> = toppings.filter {item:CustomerPrefrences ->
																												item.email == "foo@gmail.com" &&
																												item.toppings.size == 1 &&
																												// should be case insensative
																												item.toppings.containsAll(listOf("sausage"))}
		// check that we didn't mess with other peoples preferences																												
		val bar:List<CustomerPrefrences> = toppings.filter {item:CustomerPrefrences ->
																										item.email == "bar@gmail.com" &&
																										item.toppings.size == 1 &&
																										item.toppings.containsAll(listOf("cheese"))}
		assertThat(foo.size, Is(1))
		assertThat(bar.size, Is(1))
	}


	@Test
	fun shouldReturnTimsFavorite(){
		// set up fixture data
		basicTestData()

		// get tims fav
		val resp = get<List<Topping>>(client, "/api/timsfavorite") ?: listOf()
		val fav:List<Topping> = resp.filter {item:Topping ->
																					item.topping == "mushrooms"}
		assertThat(fav.size, Is(1))
	}

}