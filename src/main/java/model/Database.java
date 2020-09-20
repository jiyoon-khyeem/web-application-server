package model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {
	private static Map<String, User> users = new HashMap<String, User>();
	private static Collection<User> userList = new ArrayList<User>();
	
	public static void addUser(User user) {
		users.put(user.getUserId(), user);
		userList.add(user);
	}
	
	public static User getUser(String userId) {
		return users.get(userId);
	}
	
	public static Collection<User> findAll(){
		return userList;
	}
	
}
