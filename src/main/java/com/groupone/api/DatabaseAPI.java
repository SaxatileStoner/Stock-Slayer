package com.groupone.api;

import com.groupone.model.Stock;
import com.groupone.model.User;
import com.groupone.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.List;
import java.util.Objects;

@Component
public class DatabaseAPI implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseAPI(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        initDatabase();
        displayData();
    }

    private void initDatabase() {
        createUsersTable();
        createStocksTable();
    }

    private void createUsersTable() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS user(" +
            "id             INTEGER PRIMARY KEY," +
            "email          STRING NOT NULL," +
            "password       STRING NOT NULL," +
            "availableFunds DOUBLE," +
            "isLocked       BOOLEAN);");
    }

    private void createStocksTable() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS stock(" +
            "id         INTEGER PRIMARY KEY," +
            "ownerId    INTEGER NOT NULL REFERENCES user (id)," +
            "symbol     STRING NOT NULL," +
            "volume     DOUBLE," +
            "value      DOUBLE," +
            "FOREIGN KEY (ownerId) REFERENCES user (id));");
    }

    private void displayData() {
        List<User> users = fetchUsers();
        List<Stock> stocks = fetchStocks();
        users = pairUsersToStocks(users, stocks);

        if (users.isEmpty() && stocks.isEmpty()) {
            System.out.println("No Records to Display");
        } else {
            users.forEach(System.out::println);
            stocks.forEach(System.out::println);
        }
    }

    private List<User> fetchUsers() {
        return jdbcTemplate.query("SELECT * FROM user;",
            (resultSet, rowNum) -> new User(
                resultSet.getInt("id"),
                resultSet.getString("email"),
                resultSet.getString("password"),
                resultSet.getBoolean("isLocked"),
                resultSet.getDouble("availableFunds")
            ));
    }

    private List<Stock> fetchStocks() {
        return jdbcTemplate.query("SELECT * FROM stock;",
            (resultSet, rowNum) -> new Stock(
                resultSet.getInt("id"),
                resultSet.getInt("ownerId"),
                resultSet.getString("symbol"),
                resultSet.getDouble("volume"),
                resultSet.getDouble("value")
            ));
    }

    // Assume the implementation is present for this function
    private List<User> pairUsersToStocks(List<User> users, List<Stock> stocks) {
        // Pairing logic here...
        return users;
    }
}


    public void addUserRecord(String email, String password) throws Exception{
        if(getUserRecord(new User(email, password)) != null) throw new Exception("User already in database");

        final String addSql = "INSERT INTO user (email, password) VALUES (?, ?)";
        Object[] params = {email, password};
        int[] types = {Types.VARCHAR, Types.VARCHAR};

        int row = this.jdbcTemplate.update(addSql, params, types);
        System.out.printf("Added User: %s%n", email);
    }

    public void addUserRecord(User user) throws Exception{
        String userEmail = user.getEmail();
        String userPassword = user.getPassword();
        addUserRecord(userEmail, userPassword);
    }

    public void deleteUserRecord(int userId){
        final String deleteSql = "DELETE FROM user WHERE id=?";
        Object[] params = {userId};
        int[] types = {Types.INTEGER};
        int rows = this.jdbcTemplate.update(deleteSql, params, types);
        deleteUserStocks(userId);
        System.out.printf("Deleted User: %s%n", userId);
    }

    public User getUserRecord(User user){
        User foundUser = null;
        List<User> recordedUsers = getUserTableInfo();
        for(User recordedUser : recordedUsers){
            if(Objects.equals(recordedUser.getEmail(), user.getEmail())) {
                foundUser = recordedUser;
                break;
            }
        }

        return foundUser;
    }

    public void updateUserRecord(User oldUser, User newUser){
        final String updateSql = "UPDATE user (email, password, isLocked) SET(?, ?, ?) WHERE id = ?";
        Object[] params = {newUser.getEmail(), newUser.getPassword(), newUser.getIsLocked(), oldUser.getId()};
        Object[] types = {Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.INTEGER};
        int rows = this.jdbcTemplate.update(updateSql, params, types);
        System.out.printf("Updated User: %1$s %n%2$s %n", oldUser.getId(), newUser);
    }

    public List<User> pairUsersToStocks(List<User> users, List<Stock> stocks){
        users.forEach(user -> {
            stocks.forEach(stock -> {
                if(user.getId() == stock.getOwnerId()){
                    user.addStock(stock);
                }
            });
        });
        return users;
    }

    public void addStockRecord(int ownerId, String symbol, double volume, double value) throws Exception{
        List<User> users = getUserTableInfo();
        boolean isValidOwnedId = false;
        for (User user: users) {
            if(user.getId() == ownerId){
                isValidOwnedId = true;
                break;
            }
        }

        if(!isValidOwnedId) throw new Exception("Not Valid Owner ID!");

        final String addSql = String.format("INSERT INTO stock (ownerId, symbol, volume, value) VALUES ('%1$s', '%2$s', '%3$s', '%4$s');",
                ownerId, symbol, volume, value);
        this.jdbcTemplate.execute(addSql);
        System.out.printf("Added Stock to user: %s%n", ownerId);
    }

    public void addStockRecord(Stock stock) throws Exception{
        addStockRecord(stock.getOwnerId(),
                stock.getSymbol(),
                stock.getVolume(),
                stock.getValue());
    }

    public void deleteStockRecord(int stockId) {
        jdbcTemplate.execute(String.format("DELETE FROM stock WHERE id='%s';", stockId));
        System.out.printf("Deleted Stock w/ id: %s%n", stockId);
    }

    public List<User> getUserTableInfo(){
        String selectSql = "SELECT * FROM user";
        return queryForUsers(selectSql);
    }

    public List<Stock> getStockTableInfo() {
        String selectSql = "SELECT * FROM stock";
        return queryForStocks(selectSql);
    }

    public void addAvailableFunds(User user, double funds){
        User userRecord = getUserRecord(user);
        userRecord.addFunds(funds);
        jdbcTemplate.execute(String.format("UPDATE user SET availableFunds=%1$f WHERE id=%2$s;",
                userRecord.getAvailableFunds(), userRecord.getId()));
        System.out.printf("Set availableFunds %1$s to user %2$s", userRecord.getAvailableFunds(), user.getEmail());
    }

    public void subtractAvailableFunds(User user, double funds){
        User userRecord = getUserRecord(user);
        userRecord.subtractFunds(funds);
        jdbcTemplate.execute(String.format("UPDATE user SET availableFunds=%1$f WHERE id=%2$s;",
                userRecord.getAvailableFunds(), userRecord.getId()));
        System.out.printf("Set availableFunds %1$s to user %2$s", userRecord.getAvailableFunds(), user.getEmail());
    }

    public void toggleUserLocked(User user){
        User userRecord = getUserRecord(user);
        userRecord.toggleLock();
        jdbcTemplate.execute(String.format("UPDATE user SET isLocked=%1$s WHERE id='%2$s'",
                userRecord.getIsLocked(), userRecord.getId()));
        System.out.printf("isLocked set to: %1$s user account: %2$s", userRecord.getIsLocked(), userRecord.getEmail());
    }

    private void deleteUserStocks(int userId){
        jdbcTemplate.execute(String.format("DELETE FROM stock WHERE ownerId='%s';", userId));
        System.out.printf("Deleted User: %s stocks%n", userId);
    }

    private List<User> queryForUsers(String query){
        List<User> users = jdbcTemplate.query(query,
                (resultSet, rowNum) -> new User(
                        resultSet.getInt("id"),
                        resultSet.getString("email"),
                        resultSet.getString("password"),
                        resultSet.getBoolean("isLocked"),
                        resultSet.getDouble("availableFunds")
                ));
        users = pairUsersToStocks(users, getStockTableInfo());
        return users;
    }

    private List<Stock> queryForStocks(String query){
        return jdbcTemplate.query(query,
                (resultSet, rowNum) -> new Stock(
                        resultSet.getInt("id"),
                        resultSet.getInt("ownerId"),
                        resultSet.getString("symbol"),
                        resultSet.getDouble("volume"),
                        resultSet.getDouble("value")
                ));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printDatabaseAPIStart() {
        System.out.println("Database API Running...");
    }

    @Override
    public void run(String... args) throws Exception {

    }

}

