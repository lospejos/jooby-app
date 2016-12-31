package com.mycompany.service.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.mongodb.client.MongoDatabase;
import com.mycompany.domain.shop.Cart;
import com.mycompany.domain.shop.GeoCodeResults;
import com.mycompany.domain.shop.Order;
import com.mycompany.domain.shop.OrderStatus;
import com.mycompany.service.AbstractService;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import org.jooby.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


public class OrderService extends AbstractService<Order>
{

	private final static Logger logger = LoggerFactory.getLogger(OrderService.class);
	private final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	@Inject
	private CartService cartService;
	@Inject
	private DeliveryTypeService deliveryTypeService;
	@Inject
	private PaymentTypeService paymentTypeService;
	@Inject
	private SauceService sauceService;
	@Inject
	private ProductService productService;
	@Inject
	private ColorService colorService;
	@Inject
	private MongoDatabase db;

	public OrderService()
	{
		super(Order.class);
	}

	private String generateNewOrderNumber(Request req)
	{
		Document doc = db.runCommand(new Document("$eval", "getNextSequence('orderNumber')"));
		String result = String.valueOf(doc.getDouble("retval").intValue());
		while (result.length() < 8)
		{
			result = "0" + result;
		}
		return result;
	}

	public Map<String, String> findByPhone(String phone)
	{
		Map<String, String> map = new HashMap<>();
		if (Strings.isNullOrEmpty(phone))
		{
			return map;
		}
		Order order = getBy("phone", phone);
		map.put("name", order.name);
		map.put("streetName", order.streetName);
		map.put("streetNumber", order.streetNumber);
		map.put("flat", order.flat);
		map.put("entrance", order.entrance);
		return map;
	}

	public Order placeOrder(Request req)
	{
		Optional<String> cartJson = req.session().get("cart").toOptional();
		ObjectMapper mapper = new ObjectMapper();
		try
		{
			if (!cartJson.isPresent())
			{
				return null;
			}
			Order order = mapper.readValue(cartJson.get(), Order.class);
			order.orderNumber = generateNewOrderNumber(req);
			order.orderDate = new Date();
			order.status = OrderStatus.NEW;
			//needed to solve ng-admin bug not showing embedded linked entities
			order.sauces = sauceService.getAll().stream().map(sauce -> sauce.id).collect(Collectors.toList());
			insert(order);

			return order;
		}
		catch (IOException e)
		{
			logger.error("Error placing order", e);
		}
		return null;
	}

	//todo: move to facade
	public Map<String, Object> getFetchedOrder(String orderId)
	{
		return getOrderMap(getById(orderId));
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderMap(Cart cart)
	{
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> map = objectMapper.convertValue(cart, Map.class);
		map.put("delivery", deliveryTypeService.getById(cart.deliveryId));
		map.put("paymentType", paymentTypeService.getById(cart.paymentTypeId));
		List<Map> entries = (List) map.get("entries");
		entries.forEach(entry ->
		{
			entry.put("product", productService.getById((String) entry.get("productId")));
			entry.put("color", colorService.getById((String) entry.get("colorId")));
			List<String> sauces = (List) entry.get("sauces");
			entry.put("sauces", sauces.stream().map(sauceService::getById).collect(Collectors.toList()));
		});
		return map;
	}

	public Map sendToDelivery(Map<String, Object> order) throws ParseException
	{
		Order saved = getById((String) order.get("id"));
		saved.status = OrderStatus.IN_DELIVERY;
		saved.deliveryDate = FORMAT.parse((String) order.get("deliveryDate"));
		saved.deliveryTime = (String) order.get("deliveryTime");
		saved.streetName = (String) order.get("streetName");
		saved.streetNumber = (String) order.get("streetNumber");
		saved.entrance = (String) order.get("entrance");
		saved.flat = (String) order.get("flat");
		update(saved);
		return getFetchedOrder(saved.id);
	}

	@Override
	public void onSave(Order order)
	{
		cartService.calculateCart(order);
		updateCoordinates(order);
	}

	private void updateCoordinates(Order order)
	{
		String url = String.format("http://maps.google.com/maps/api/geocode/json?address=Россия+Ярославль+%s+%s",
				order.streetName.trim().replaceAll(" ", "+"), order.streetNumber.trim());

		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		try
		{
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			String strResponse = EntityUtils.toString(entity);
			ObjectMapper mapper = new ObjectMapper();
			GeoCodeResults res = mapper.readValue(strResponse, GeoCodeResults.class);
			order.lat = res.getGeometry().getLat();
			order.lng = res.getGeometry().getLng();
		}
		catch (IOException e)
		{
			logger.error("Error getting coordinates", e);
		}

	}

	public Map cancelOrder(String id)
	{
		Order order = getById(id);
		order.status = OrderStatus.CANCELLED;
		update(order);
		return getFetchedOrder(order.id);
	}
}
