create table topping (
	id serial primary key,
	topping text not null unique
);

create table customer (
	id serial primary key,
	email text not null unique
);

create table customer_topping(
	customer_id integer references customer(id) on delete cascade,
	topping_id integer references topping(id) on delete cascade
);