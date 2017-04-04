package com.mycompany.service.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.mongodb.client.MongoDatabase;
import com.mycompany.domain.shop.*;
import com.mycompany.service.AbstractService;
import com.mycompany.util.DateUtils;
import com.mycompany.util.ObjectUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.bson.Document;
import org.jongo.MongoCursor;
import org.jooby.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


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
	private CustomerService customerService;
	@Inject
	private MongoDatabase db;
	@Inject
	private GoogleMapsService googleMapsService;

	public OrderService()
	{
		super(Order.class);
	}

	private String generateNewOrderNumber()
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
		if (!cartJson.isPresent())
		{
			return null;
		}
		ObjectMapper mapper = new ObjectMapper();
		try
		{
			Order order = mapper.readValue(cartJson.get(), Order.class);
			order.orderNumber = generateNewOrderNumber();
			order.orderDate = new Date();
			order.status = OrderStatus.NEW;
			linkToCustomer(order);
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

	public Order createOrder(Map map) throws ParseException
	{
		ObjectMapper mapper = new ObjectMapper();
		map.put("orderDate", new Date());
		map.put("deliveryDate", FORMAT.parse((String)map.get("deliveryDate")));
		map.remove("id");
		Order order = mapper.convertValue(map, Order.class);
		order.orderNumber = generateNewOrderNumber();
		order.orderDate = new Date();
		order.status = OrderStatus.IN_DELIVERY;
		linkToCustomer(order);
		//needed to solve ng-admin bug not showing embedded linked entities
		order.sauces = sauceService.getAll().stream().map(sauce -> sauce.id).collect(Collectors.toList());
		insert(order);

		return order;
	}

	private void linkToCustomer(Order order)
	{
		Customer customer = customerService.getBy("phone", order.phone);
		if (customer == null) {
			customer = new Customer();
			customer.name = order.name;
			customer.phone = order.phone;
			customer.address = String.format("%s %s %s", order.streetName, order.streetNumber, order.flat);
			customer = customerService.insert(customer);
		}
		order.customerId = customer.id;
	}

	public Map<String, Object> getFetchedOrder(String orderId)
	{
		return getOrderMap(getById(orderId));
	}

	public Map<String, Object> getFetchedOrderByNumber(String orderNumber)
	{
		if ("new".equals(orderNumber)) {
			return getOrderMap(cartService.getNewCart());
		}
		return getOrderMap(getBy("orderNumber", orderNumber));
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderMap(Cart cart)
	{
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> map = objectMapper.convertValue(cart, Map.class);
		map.put("delivery", deliveryTypeService.getById(cart.deliveryId));
		map.put("paymentType", paymentTypeService.getById(cart.paymentTypeId));
		if (map.get("deliveryDate") != null) {
			map.put("deliveryDate", new Date((Long)map.get("deliveryDate")));
		}
		List<Map> entries = (List) map.get("entries");
		entries.forEach(entry ->
		{
			entry.put("product", productService.getById((String) entry.get("productId")));
			if (entry.get("colorId") != null) {
				entry.put("color", colorService.getById((String) entry.get("colorId")));
			}
			if (entry.get("sauces") != null) {
				List<String> sauces = (List) entry.get("sauces");
				entry.put("sauces", sauces.stream().map(sauceService::getById).collect(Collectors.toList()));
			}
		});
		return map;
	}

	public Map sendToDelivery(Map<String, Object> order) throws ParseException
	{
		Order saved = getById(ObjectUtil.getValue(order, "id"));
		Boolean toDelivery = ObjectUtil.getValue(order, "toDelivery");
		if (BooleanUtils.isTrue(toDelivery)) {
			saved.status = OrderStatus.IN_DELIVERY;
		} else if (saved.status == OrderStatus.NEW) {
			saved.status = OrderStatus.CLARIFICATION;
		}
		saved.deliveryDate = FORMAT.parse((String) order.get("deliveryDate"));
		saved.deliveryId = ObjectUtil.getValue(order, "delivery.id");
		saved.deliveryTime = ObjectUtil.getValue(order, "deliveryTime");
		saved.streetName = ObjectUtil.getValue(order, "streetName");
		saved.streetNumber = ObjectUtil.getValue(order, "streetNumber");
		saved.entrance = ObjectUtil.getValue(order, "entrance");
		saved.flat = ObjectUtil.getValue(order, "flat");
		update(saved);
		return getFetchedOrder(saved.id);
	}

	@Override
	public void onSave(Order order)
	{
		cartService.calculateCart(order);
		linkToCustomer(order);
		updateCoordinates(order);
	}

	private void updateCoordinates(Order order)
	{
		Geometry geometry = googleMapsService.getCoordinates(order.streetName, order.streetNumber);
		if (geometry != null) {
			order.lat = geometry.getLat();
			order.lng = geometry.getLng();
		}
	}

	public Map cancelOrder(String id)
	{
		Order order = getById(id);
		order.status = OrderStatus.CANCELLED;
		update(order);
		return getFetchedOrder(order.id);
	}

	public List<Map<String, Object>> getDeliverySchedule(String dateStr, String time)
	{
		Date dateStart = DateUtils.safeParseUTC(dateStr);
		Date dateEnd = new Date(dateStart.getTime() + 1000 * 3600 * 24);
		MongoCursor<Order> cursor = getCollection()
				.find("{deliveryDate:{$gte:#,$lt:#}, deliveryTime:#}", dateStart, dateEnd, time).as(Order.class);
		return StreamSupport.stream(cursor.spliterator(), false).map(this::getOrderMap).collect(Collectors.toList());
	}
}
