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
    
    public static String postHttp_auth(String url, String publicKey, String privateKey, List<NameValuePair> params) throws NullPointerException {
        try {
            HttpPost post = new HttpPost(url);
            post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            String auth = publicKey + ":" + privateKey;
            byte[] encodedAuth = Base64.getEncoder().encode(
                    // .encodeBase64(
                    auth.getBytes(StandardCharsets.ISO_8859_1));
            String authHeader = "Basic " + new String(encodedAuth);
            post.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpResponse response = httpClient.execute(post);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);

            }

        } catch (Exception e) {
            Logger.logException("Making url_encoded post request to url " + url, e, true);
        }
        return null;
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

        @Override
    public Object placeSellOrder(String adress, String privateKey, String pair, double amount, double price) throws InvalidApiException, SymbolNotExistsException, InterruptedException {
        String answerStr = "";
        try {
            DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
            otherSymbols.setGroupingSeparator('.');
            DecimalFormat format = new DecimalFormat("##.########", otherSymbols);

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("blockchainType", "ethereum"));
            params.add(new BasicNameValuePair("fromCurrencyId",currencyIds.get(getCounter(pair))));
            params.add(new BasicNameValuePair("toCurrencyId",currencyIds.get(getBase(pair)) ));
            params.add(new BasicNameValuePair("amount", format.format(amount)));
            params.add(new BasicNameValuePair("minimumReturn", format.format(new BigDecimal(amount).multiply(new BigDecimal(price)).multiply(new BigDecimal(0.98)))));
            params.add(new BasicNameValuePair("ownerAddress", adress));
            System.out.println(params);
            answerStr = RequestsHelper.postHttp(order_url,params,null);
            System.out.println(answerStr);
            return null;
        } catch (Exception e) {
            Logger.logException("Exception while placing  sell order. Received responce = " + answerStr, e, false);
            Thread.sleep(Resources.DELAY);
            return placeBuyOrder(adress, privateKey, pair, amount, price);
        }
    }
    @Override
    public double getAmountByCurrency(String publicKey, String privateKey, String pair, boolean currency) throws InterruptedException, SymbolNotExistsException {

        String seachedCurrency = getCurrency(pair, currency);
        String balance = getBalance(publicKey, privateKey);
        JSONObject jsonObject = new JSONObject(balance);
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
}
