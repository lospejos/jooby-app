package com.mycompany.domain.shop;

import com.mycompany.annotation.Deployment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Deployment(table = "orders")
public class Order extends Cart
{
	public String orderNumber;

	public Date orderDate;

	public OrderStatus status;

	//needed to solve ng-admin bug not showing embedded linked entities
	public List<String> sauces = new ArrayList<>();

	public String lat, lng;

	@Override
	public String getFullText()
	{
		return orderNumber + "_" +name.toLowerCase() + "_" + phone;
	}

}
