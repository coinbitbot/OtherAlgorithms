package coinbot.modules.resources.stocks;

import coinbot.modules.entities.OrderEntity;
import coinbot.modules.exceptions.InvalidApiException;
import coinbot.modules.exceptions.SymbolNotExistsException;
import coinbot.modules.exceptions.UnexpectedStockStateException;
import coinbot.modules.models.PriceWithVolume;
import coinbot.modules.models.Ticker;
import coinbot.modules.resources.enums.Stocks;
import coinbot.modules.services.volumes.VolumeService;
import coinbot.modules.utils.Logger;
import coinbot.modules.utils.RequestsHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;


public class Bot2 {
    private static final String getTickerUrl = "https://api.coinbene.com/v1/market/ticker";
    private static final String placeOrderUrl = "https://api.coinbene.com/v1/trade/order/place";
    private static final String getActiveOrdersUrl = "https://api.coinbene.com/v1/trade/order/open-orders";
    private static final String getBalanceUrl = "https://api.coinbene.com/v1/trade/balance";
    private final String GET_ORDER_BOOK = "https://api.coinbene.com/v1/market/orderbook";
    private final String CANCEL_ORDER = "https://api.coinbene.com/v1/trade/order/cancel";
    private final String GET_BALANCE = "https://api.coinbene.com/v1/trade/balance";
    private final String GET_TICKER = "https://api.coinbene.com/v1/market/ticker";


    private static Map<String, Double> amounts = new HashMap<>();

    @Override
    public boolean isAPIKeysValid(String publicKey, String privateKey) throws InvalidApiException {
        String answer = "";
        try {
            answer = makePrivateRequest(getBalanceUrl, createParamsBalance(publicKey, privateKey, "exchange"));
            JSONObject json = new JSONObject(answer);
            return json.has("balance");
        } catch (Exception e) {
            Logger.logException("While checking API keys got answer " + answer, e, true);
            throw new InvalidApiException("While checking API keys got bad answer");
        }
    }

    // NO
    @Override
    public double getBTCAmount(String publicKey, String privateKey) {
        return 0;
    }

    @Override
    public Object placeBuyOrder(String publicKey, String privateKey, String pair, double amount, double price) throws InvalidApiException, SymbolNotExistsException {
        String answer = "";
        try {
            //System.out.println("amount: " + amount + "\nprice:" + price);
            answer = makePrivateRequest(placeOrderUrl, createParamsForTrade(publicKey, privateKey, true, pair, amount, price));
            System.out.println("answer = " + answer);
            JSONObject json = new JSONObject(answer);
            if (json.getString("status").equals("error")) {
                return null;
            }
            amounts.put(json.getString("orderid"), amount);
            return new OrderEntity(json.getString("orderid"), new Timestamp(json.getLong("timestamp")),
                    pair, amount, price, json.getString("status"));

        } catch (Exception e) {
            JSONObject json = new JSONObject(answer);
            //  JSONObject err = json.getJSONObject("error");
            Logger.logException("While placing buy order " + answer, e, false);
            throw new InvalidApiException(answer);
        }
    }

    @Override
    public Object placeSellOrder(String publicKey, String privateKey, String pair, double amount, double price) throws InvalidApiException, SymbolNotExistsException {
        String answer = "";
        try {
            answer = makePrivateRequest(placeOrderUrl, createParamsForTrade(publicKey, privateKey, false, pair, amount, price));
            JSONObject json = new JSONObject(answer);
            System.out.println("answer = " + answer);
            if (json.getString("status").equals("error")) return null;

            return new OrderEntity(json.getString("orderid"), new Timestamp(json.getLong("timestamp")),
                    pair, amount, price, json.getString("status"));
        } catch (Exception e) {
            //8System.out.println("Answer: " + answer);
            JSONObject json = new JSONObject(answer);
            //       String err = json.getString("description");
            Logger.logException("While placing sell order " + answer, e, false);
            throw new InvalidApiException(answer);
        }
    }

    @Override
    public double getPairPrice(String pair) throws SymbolNotExistsException, UnexpectedStockStateException {
        String answerStr = "";
        try {
            answerStr = RequestsHelper.getHttp(getTickerUrl + "?symbol=" + encodePair(pair), null);
            if (answerStr == null || !answerStr.startsWith("{") || !answerStr.endsWith("}"))
                throw new UnexpectedStockStateException();

            return new JSONObject(answerStr).optJSONArray("ticker").getJSONObject(0).getDouble("bid");
        } catch (JSONException e) {
            Logger.logException("While getting " + encodePair(pair) + " price got response: " + answerStr, e, false);
            throw new SymbolNotExistsException();
        }
    }

    @Override
    public List<PriceWithVolume> getOrderBook(String symbol, boolean buy, double maximalPrice) throws UnexpectedStockStateException, InterruptedException {
        String answer = null;
        List<PriceWithVolume> orderBook = new ArrayList<>();
        try {
            //System.out.println(encodePair(symbol));
            String url = GET_ORDER_BOOK + "?symbol=" + encodePair(symbol) + "&depth=500";
            answer = RequestsHelper.getHttp(url, null);

            JSONObject jsonObject = new JSONObject(answer).getJSONObject("orderbook");
            String res = buy ? "bids" : "asks";
            jsonObject.getJSONArray(res).forEach(o -> {
                JSONObject json = new JSONObject(o.toString());
                double price = json.getDouble("price");
                double amount = json.getDouble("quantity");
                orderBook.add(new PriceWithVolume(price, amount));
            });
        } catch (Exception e) {
            Logger.logException("While retrieving order book got answer: " + answer, e, false);
            throw new UnexpectedStockStateException();
        }
        return orderBook;
    }

    @Override
    public boolean cancelOrder(String publicKey, String privateKey, String order_id, String pair) throws InvalidApiException, UnexpectedStockStateException {
        String answer = null;
        try {
            String orderId = order_id;
            Date now = new Date();
            BasicNameValuePair secret = new BasicNameValuePair("secret", privateKey);
            BasicNameValuePair apiid = new BasicNameValuePair("apiid", publicKey);
            BasicNameValuePair orderid = new BasicNameValuePair("orderid", order_id);
            BasicNameValuePair timestamp = new BasicNameValuePair("timestamp", String.valueOf(now.getTime()));
            String singStr = generateSign(apiid, secret, timestamp, orderid);
            BasicNameValuePair sign = new BasicNameValuePair("sign", singStr);

            List<NameValuePair> bodyParam = new ArrayList<>();
            bodyParam.add(apiid);
            bodyParam.add(orderid);
            bodyParam.add(timestamp);
            bodyParam.add(sign);

            answer = makePrivateRequest(CANCEL_ORDER, bodyParam);

            System.out.println("answer = " + answer);
            JSONObject jsonAnswer = new JSONObject(answer);

            if (jsonAnswer.has("description") &&
                    (jsonAnswer.getString("description").equals("Cancellation failed") || jsonAnswer.getString("description").equals("The order does not exist, the cancellation of failure"))) {
                VolumeService.minusVolume(Stocks.Coinbene, pair, getAmountById(order_id));
                return true;
            }

            if (jsonAnswer.has("status") && jsonAnswer.getString("status").equals("ok")) return true;

            return false;
        } catch (Exception e) {
            Logger.logException("While deleting  order ( coinbene ) " + answer, e, false);
            throw new InvalidApiException(answer);
        }

    }

    private double getAmountById(String order_id) {
        double amount = 0;
        if (amounts.containsKey(order_id)) {
            amount = amounts.get(order_id);
            amounts.remove(order_id);
        }
        return amount;
    }




    @Override
    public double getAmountByCurrency(String publicKey, String privateKey, String pair, boolean currency) throws InterruptedException, SymbolNotExistsException {

        String seachedCurrency = getCurrency(pair, currency);
        String balance = getBalance(publicKey, privateKey);
        JSONObject jsonObject = new JSONObject(balance);
        //System.out.println(jsonObject);
        JSONArray array = jsonObject.getJSONArray("balance");
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj.getString("asset").equals(seachedCurrency))
                return obj.getDouble("total");
        }
        return -1;
    }

    @Override
    public List<OrderEntity> getActiveOrders(String publicKey, String privateKey, String pair) throws InterruptedException {
        List<OrderEntity> orders = new ArrayList<>();
        String answer = "";
        try {
            answer = makePrivateRequest(getActiveOrdersUrl, createParamsForOpenOrders(publicKey, privateKey, pair));
            Object ords = new JSONObject(answer).get("orders");

            if (!(String.valueOf(ords).equals("null"))) {
                JSONArray orderArr = ((JSONObject) ords).getJSONArray("result");
                for (int i = 0; i < orderArr.length(); i++) {
                    JSONObject jsonOrder = (JSONObject) orderArr.getJSONObject(i);
                    if (jsonOrder.getString("symbol").equals(encodePair(pair))) {
                        OrderEntity order = new OrderEntity(jsonOrder.getString("orderid"), new Timestamp(jsonOrder.getLong("createtime")),
                                pair, jsonOrder.getDouble("orderquantity"), jsonOrder.getDouble("price"), jsonOrder.getString("orderstatus"));
                        orders.add(order);
                    }
                }

            }
        } catch (Exception e) {
            Logger.logException("While getting active orders got response: " + answer, e, false);
        }
        return orders;
    }



    @Override
    public Ticker getTicker(String symbol) throws SymbolNotExistsException {
        String answerStr = "";
        String encoded = encodePair(symbol);
        try {
            answerStr = RequestsHelper.getHttp(getTickerUrl + "?symbol=" + encoded, null);
            JSONObject obj = new JSONObject(answerStr).getJSONArray("ticker").getJSONObject(0);
            System.out.println(obj);
            return new Ticker(obj.getDouble("ask"), obj.getDouble("bid"), obj.getDouble("24hrAmt"));
        } catch (Exception e) {
            Logger.logException("While getting ticker got answer: " + answerStr, e, false);
            throw new SymbolNotExistsException();
        }
    }


    @Override
    public BigDecimal getDaysVolume(String pair) throws SymbolNotExistsException {
        String answer = null;
        try {
            String param = encodePair(pair);
            answer = RequestsHelper.getHttp(getTickerUrl + "?symbol=" + param, null);
            JSONObject ticker = new JSONObject(answer).getJSONArray("ticker").getJSONObject(0);
            BigDecimal volume = new BigDecimal(ticker.getString("24hrVol"));
            return volume;
        } catch (Exception e) {
            Logger.logException("While getting ticker got answer: " + answer, e, false);
            throw new SymbolNotExistsException();
        }
    }

    private String getBalance(String publicKey, String privateKey) {

        String answer = null;
        try {
            Date now = new Date();

            BasicNameValuePair apiid = new BasicNameValuePair("apiid", publicKey);
            BasicNameValuePair secret = new BasicNameValuePair("secret", privateKey);
            BasicNameValuePair timestamp = new BasicNameValuePair("timestamp", String.valueOf(now.getTime()));
            BasicNameValuePair acc = new BasicNameValuePair("account", "exchange");

            String singStr = generateSign(apiid, secret, timestamp, acc);
            List<NameValuePair> params = new ArrayList<>();
            params.add(apiid);
            params.add(timestamp);
            params.add(new BasicNameValuePair("sign", singStr));
            params.add(acc);
            System.out.println(generateJSON(params));
            answer = makePrivateRequest(GET_BALANCE, params);
        } catch (Exception e) {
            Logger.logException("While get balance" + answer, e, false);
        }
        return answer;
    }

    private String getCurrency(String pair, boolean currency) {
        if (currency) return pair.substring(0, pair.indexOf('-'));
        else return pair.substring(pair.indexOf('-') + 1);
    }

    private List<NameValuePair> createParamsForTrade(String apiid, String privateKey, boolean buy, String symbol, double quantity, double price) {
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
        otherSymbols.setGroupingSeparator('.');
        DecimalFormat formatPrice = new DecimalFormat("####.########", otherSymbols);
        DecimalFormat formatAmount = new DecimalFormat("####.##", otherSymbols);

        BasicNameValuePair apiidVP = new BasicNameValuePair("apiid", apiid);
        BasicNameValuePair priceVP = new BasicNameValuePair("price", formatPrice.format(price));

        BasicNameValuePair quantityVP = new BasicNameValuePair("quantity", formatAmount.format(quantity));
        BasicNameValuePair symbolVP = new BasicNameValuePair("symbol", encodePair(symbol));
        BasicNameValuePair typeVP = new BasicNameValuePair("type", buy ? "buy-limit" : "sell-limit");
        BasicNameValuePair timestampVP = new BasicNameValuePair("timestamp", String.valueOf((long) Math.floor(System.currentTimeMillis())));
        BasicNameValuePair secretVP = new BasicNameValuePair("secret", privateKey);
        BasicNameValuePair signVP = new BasicNameValuePair("sign", generateSign(apiidVP, priceVP, quantityVP, symbolVP, typeVP, timestampVP, secretVP));
        if (buy) VolumeService.addVolume(Stocks.Coinbene, symbol, Double.parseDouble(formatAmount.format(quantity)));
        return new ArrayList<>(Arrays.asList(apiidVP, priceVP, quantityVP, symbolVP, typeVP, timestampVP, signVP));
    }

    private List<NameValuePair> createParamsForOpenOrders(String apiid, String privateKey, String symbol) {
        BasicNameValuePair apiidVP = new BasicNameValuePair("apiid", apiid);
        BasicNameValuePair timestampVP = new BasicNameValuePair("timestamp", String.valueOf((long) Math.floor(System.currentTimeMillis())));
        BasicNameValuePair secretVP = new BasicNameValuePair("secret", privateKey);
        BasicNameValuePair symbolVP = new BasicNameValuePair("symbol", encodePair(symbol));
        BasicNameValuePair signVP = new BasicNameValuePair("sign", generateSign(apiidVP, secretVP, symbolVP, timestampVP));

        return new ArrayList<>(Arrays.asList(apiidVP, timestampVP, signVP, symbolVP));
    }

    private List<NameValuePair> createParamsBalance(String apiid, String privateKey, String account) {
        BasicNameValuePair accountVP = new BasicNameValuePair("account", account);
        BasicNameValuePair apiidVP = new BasicNameValuePair("apiid", apiid);
        BasicNameValuePair timestampVP = new BasicNameValuePair("timestamp", String.valueOf((long) Math.floor(System.currentTimeMillis())));
        BasicNameValuePair secretVP = new BasicNameValuePair("secret", privateKey);
        BasicNameValuePair signVP = new BasicNameValuePair("sign", generateSign(accountVP, apiidVP, timestampVP, secretVP));
        return new ArrayList<>(Arrays.asList(accountVP, apiidVP, timestampVP, signVP));
    }


    private String generateSign(BasicNameValuePair... params) {
        List<String> paramList = Arrays.stream(params).map(e -> e.getName().toUpperCase() + "=" + e.getValue().toUpperCase()).collect(Collectors.toList());

        Collections.sort(paramList);
        String paramUrl = paramList.stream().map(Object::toString).collect(Collectors.joining("&"));
        String sign = DigestUtils.md5Hex(paramUrl);

        return sign;
    }

    private String generateJSON(List<NameValuePair> params) {
        JSONObject result = null;
        try {
            result = new JSONObject();
            for (NameValuePair o : params) {
                result.put(o.getName(), o.getValue());
            }
        } catch (Exception e) {
            Logger.logException("While generating json from list  got exception...", e, false);
        }
        return result.toString();
    }

    private String makePrivateRequest(String url, List<NameValuePair> params) {
        try {
            List<NameValuePair> httpHeaders = new ArrayList<>();
            httpHeaders.add(new BasicNameValuePair("Content-Type", "application/json"));
            return RequestsHelper.postHttp(url, generateJSON(params), httpHeaders);
        } catch (Exception e) {
            Logger.logException("While sending private request", e, true);
        }
        return null;

    }

    private String encodePair(String pair) {
        return pair.substring(0, pair.indexOf('-')) + pair.substring(pair.indexOf('-') + 1);
    }

}
