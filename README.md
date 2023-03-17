# Pizza API
The Pizza API allows the user to:
- Register an email, along with thier topping preferences
- See which toppings have been created, along with how popular each one is
- See everyones topping preferences

## Install/Setup

### Java 
Make sure Java 17 is installed - this will install Java 17 on Ubuntu, see online docs
for how to install it on your system
```
sudo apt install openjdk-17-jdk openjdk-17-jre zip
```

### Docker
We will be running a postgres instance in docker, so make sure you have [docker installed](https://docs.docker.com/engine/install/)
```
docker run --name some-postgres -e POSTGRES_PASSWORD=postgres -d -p5432:5432 postgres
```

This command will start a postgres instance on your machine with username `postgres`, password `postgres` and a default db called `postgres`

### Running Tests
```
./gradlew test
```
This command will run a suite of integration tests to ensure the system is working properly. It will automatically run the set of flyway migrations, so make sure your pg instance is up and running.

### Running the API
```
./gradlew bootRun
```
This command will start an embedded Tomcat server at `http://localhost:8080/api/`

### Example Usage
#### Get Toppings
```
GET /api/
Body: none

Returns:
[{"id":13,"topping":"cheese","customerCount":2},{"id":14,"topping":"pepperoni","customerCount":1}]
```

#### Register Customer
```
PUT /api/register/{email}
Body: ["cheese", "pepperoni"]
Returns: ok or 400 if email is malformed
```

#### Get All Customers
```
GET /api/toppingsByCustomer
Body: none
Returns: 
[{"id":11,"email":"foo@gmail.com","toppings":["cheese","pepperoni"]},{"id":12,"email":"bar@gmail.com","toppings":["cheese"]}]
```

#### Get My Favorite Topping
```
GET /api/timsfavorite
Body: none
Returns: [{"id":18,"topping":"mushrooms"}]
```