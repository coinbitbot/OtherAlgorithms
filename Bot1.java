
import com.sun.javafx.collections.MappingChange;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestMethod;

import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.logging.Logger;
import coinbot.modules.exceptions.InvalidApiException;
import coinbot.modules.exceptions.SymbolNotExistsException;
import coinbot.modules.exceptions.UnexpectedStockStateException;
import coinbot.modules.models.PriceWithVolume;
import coinbot.modules.resources.Resources;
import coinbot.modules.models.Ticker;
import coinbot.modules.resources.enums.RequestMethod;
import coinbot.modules.resources.enums.Stocks;
import coinbot.modules.services.volumes.VolumeService;
import coinbot.modules.services.telegram.SenderMessage;
import coinbot.modules.utils.Logger;
import coinbot.modules.utils.RequestsHelper;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;
public class Bot1 {


    private final static String TIDEX_URL = "https://api.tidex.com/tapi";
    private static final String TIDEX_TICKER = "https://api.tidex.com/api/3/ticker/";
    //  private static final String TIDEX_INFO = "https://api.tidex.com/api/3/info";

    private static final String TIDEX_DEPTH = "https://api.tidex.com/api/3/depth/";

    private static Map<String, Double> amounts = new HashMap<>();

    public boolean isAPIKeysValid(String publicKey, String privateKey) throws InvalidApiException {
        String answer = "";
        List<NameValuePair> params = new ArrayList<>();
        try {
            params.add(new BasicNameValuePair("method", "getInfo"));
            params.add(new BasicNameValuePair("nonce", String.valueOf(System.currentTimeMillis() / 1000)));
            answer = makePrivateRequest(TIDEX_URL, RequestMethod.POST, params, publicKey, privateKey);
            JSONObject json = new JSONObject(answer);
            return json.getInt("success") == 1;
        } catch (Exception e) {
            Logger.logException("While checking API keys got answer " + answer, e, true);
            return false;
        }
    }

    private String encodePair(String pair) {
        return pair.replace("-", "_").toLowerCase();
    }


    public double getBTCAmount(String publicKey, String privateKey) {
        return 0;
    }

    public Object placeBuyOrder(String publicKey, String privateKey, String pair, double amount, double price) throws InvalidApiException, SymbolNotExistsException, InterruptedException {
        return buy$sell(publicKey, privateKey, pair, amount, price, true);
    }

    public Object placeSellOrder(String publicKey, String privateKey, String pair, double amount, double price) throws InvalidApiException, SymbolNotExistsException, InterruptedException {
        return buy$sell(publicKey, privateKey, pair, amount, price, false);
    }

    private Object buy$sell(String publicKey, String privateKey, String pair, double amount, double price, boolean type) throws InvalidApiException, SymbolNotExistsException, InterruptedException {
        String answerStr = "";
        try {

            DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
            otherSymbols.setGroupingSeparator('.');
            DecimalFormat format = new DecimalFormat("##.########", otherSymbols);

            List<NameValuePair> getParams = new ArrayList<>();
            getParams.add(new BasicNameValuePair("method", "Trade"));
            getParams.add(new BasicNameValuePair("nonce", String.valueOf(System.currentTimeMillis() / 1000)));
            getParams.addAll(createParamsForTrade(type, pair, format.format(amount), format.format(price)));
            answerStr = makePrivateRequest(TIDEX_URL, RequestMethod.POST, getParams, publicKey, privateKey);
            //System.out.println(" ===== "  + answerStr);
            JSONObject answer = new JSONObject(answerStr);
            JSONObject json = answer.getJSONObject("return");
            if (answer.keySet().size() == 0) return null;
            String order_id = json.getLong("order_id") + "";
            double amount_order = Double.parseDouble(format.format(amount));
            //---------------------------------
            if (type) amounts.put(order_id, amount_order);
            //---------------------------------));
            return new OrderEntity(order_id, getTimestamp(new Date().getTime()), pair, amount_order, Double.parseDouble(format.format(price)), "1");
        } catch (Exception e) {
            System.out.println("Pizdec---------------------------------------------------");
            String byu$sell = type ? "buy" : "sell";
            Logger.logException("Exception while placing " + byu$sell + " order. Received responce = " + answerStr, e, false);
            Thread.sleep(Resources.DELAY);
            return buy$sell(publicKey, privateKey, pair, amount, price, type);
        }
    }

    private List<NameValuePair> createParamsForTrade(boolean b, String pair, String amount, String price) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("pair", encodePair(pair)));
        params.add(new BasicNameValuePair("type", b ? "buy" : "sell"));
        params.add(new BasicNameValuePair("rate", price));
        params.add(new BasicNameValuePair("amount", amount));
        if (b) VolumeService.addVolume(Stocks.Tidex, pair, Double.parseDouble(amount));
        return params;
    }

    @Override
    public double getPairPrice(String pair) throws SymbolNotExistsException, UnexpectedStockStateException {
        String answerStr = "";
        double price = -1;
        String encoded = encodePair(pair);

        try {
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("param", encoded));
            answerStr = makePrivateRequest(TIDEX_TICKER, RequestMethod.GET, params, null, null);
            JSONObject obj = new JSONObject(answerStr);
            JSONObject ticker = obj.getJSONObject(encoded);
            price = ticker.getDouble("last");
        } catch (Exception e) {
            Logger.logException("While getting " + encoded + " price got response: " + answerStr, e, false);
        }
        return price;
    }

    @Override
    public Ticker getTicker(String symbol) throws SymbolNotExistsException {
        String answerStr = "";
        String encoded = encodePair(symbol);
        List<NameValuePair> param = new ArrayList<>();
        param.add(new BasicNameValuePair("param", encoded));
        try {
            answerStr = makePrivateRequest(TIDEX_TICKER, RequestMethod.GET, param, null, null);
            JSONObject obj = new JSONObject(answerStr);
            JSONObject ticker = obj.getJSONObject(encoded);
            return new Ticker(ticker.getDouble("sell"), ticker.getDouble("buy"), ticker.getDouble("vol"));
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
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("param", param));
            answer = makePrivateRequest(TIDEX_TICKER, RequestMethod.GET, params, null, null);
            JSONObject obj = new JSONObject(answer);
            JSONObject ticker = obj.getJSONObject(param);
            String jsonVolume = String.valueOf(ticker.getDouble("vol_cur"));
            BigDecimal volume = new BigDecimal(jsonVolume);
            return volume;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.logException("While getting ticker got answer: " + answer, e, false);
            throw new SymbolNotExistsException();
        }
    }

    @Override
    public List<PriceWithVolume> getOrderBook(String symbol, boolean buy, double maximalPrice) throws UnexpectedStockStateException, InterruptedException {
        List<PriceWithVolume> ret = new ArrayList<>();
        String answerStr = "";
        String encoded = encodePair(symbol);
        try {
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("param", encoded));
            answerStr = makePrivateRequest(TIDEX_DEPTH, RequestMethod.GET, params, null, null);
            String res = buy ? "bids" : "asks";
            JSONObject json = new JSONObject(answerStr);
            json.getJSONObject(encoded).getJSONArray(res).forEach(o -> {
                JSONArray obj = new JSONArray(o.toString());
                ret.add(new PriceWithVolume(obj.getDouble(0), obj.getDouble(1)));
            });
        } catch (Exception e) {
            Logger.logException("While retrieving order book got answer: " + answerStr, e, false);
            Thread.sleep(Resources.DELAY);
            return getOrderBook(symbol, buy, maximalPrice);
        }
        return ret;
    }

    @Override
    public boolean cancelOrder(String publicKey, String privateKey, String order_id, String pair) throws InvalidApiException, UnexpectedStockStateException, InterruptedException {
        String answer = "";
        try {
            List<NameValuePair> getParams = new ArrayList<>();
            getParams.add(new BasicNameValuePair("method", "CancelOrder"));
            getParams.add(new BasicNameValuePair("nonce", String.valueOf(System.currentTimeMillis() / 1000)));
            getParams.add(new BasicNameValuePair("order_id", order_id));
            answer = makePrivateRequest(TIDEX_URL, RequestMethod.POST, getParams, publicKey, privateKey);
            JSONObject json = new JSONObject(answer);
            if (json.getInt("success") == 0 && json.getInt("code") == 833)
                return true;
            if (json.getInt("success") == 1) {
                VolumeService.minusVolume(Stocks.Tidex, pair, getAmountById(order_id));
                return true;
            }
            return false;
        } catch (Exception e) {
            Logger.logException("While deleting  order " + answer, e, false);
            Thread.sleep(Resources.DELAY);
            return cancelOrder(publicKey, privateKey, order_id, pair);
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
        String answer = "";
        double amount = -1;
        List<NameValuePair> params = new ArrayList<>();
        try {
            params.add(new BasicNameValuePair("method", "getInfo"));
            params.add(new BasicNameValuePair("nonce", String.valueOf(System.currentTimeMillis() / 1000)));
            String curren = getCurrency(pair, currency);
            answer = makePrivateRequest(TIDEX_URL, RequestMethod.POST, params, publicKey, privateKey);
            JSONObject json = new JSONObject(answer);
            JSONObject info = json.getJSONObject("return");
            JSONObject funds = info.getJSONObject("funds");
            amount = funds.getDouble(curren);
        } catch (Exception e) {
            Logger.logException("While getting: " + currency + " amount got answer " + answer, e, false);
        }
        return amount;
    }

    private String getCurrency(String pair, boolean currency) {
        if (currency) return pair.substring(0, pair.indexOf('-')).toLowerCase();
        else return pair.substring(pair.indexOf('-') + 1).toLowerCase();

    }


    @Override
    public List<OrderEntity> getActiveOrders(String publicKey, String privateKey, String pair) throws InterruptedException {
        List<OrderEntity> orders = new ArrayList<>();
        List<NameValuePair> getParams = new ArrayList<>();
        String answer = "";
        try {
            getParams.add(new BasicNameValuePair("method", "ActiveOrders"));
            getParams.add(new BasicNameValuePair("nonce", String.valueOf(System.currentTimeMillis() / 1000)));
            getParams.add(new BasicNameValuePair("pair", encodePair(pair)));
            answer = makePrivateRequest(TIDEX_URL, RequestMethod.POST, getParams, publicKey, privateKey);
            JSONObject obj = new JSONObject(answer);
            if (obj.has("return")) {
                JSONObject result = obj.getJSONObject("return");
                Set<String> keys = result.keySet();
                for (String key : keys) {
                    JSONObject json = result.getJSONObject(key);
                    OrderEntity order = new OrderEntity(key, getTimestamp(json.getLong("timestamp_created")), pair, json.getDouble("amount"),
                            json.getDouble("rate"), json.getInt("status") + "");
                    orders.add(order);
                }
            }
            return orders;
        } catch (Exception e) {
            Logger.logException("While getting active orders got response: " + answer, e, false);
            Thread.sleep(Resources.DELAY);
            return getActiveOrders(publicKey, privateKey, pair);
        }
    }

    private Timestamp getTimestamp(Long str) {
        try {
            return new Timestamp(str);
        } catch (Exception e) { //this generic but you can control another types of exception
            Logger.logException("While parsing timestamp: ", e, false);
            return null;
        }
    }


    private String makePrivateRequest(String url, RequestMethod method, List<NameValuePair> params, String publicKey, String privateKey) {
        String answer = "";
        try {
            StringBuilder sb = new StringBuilder();
            for (NameValuePair postParam : params) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(postParam.getName()).append("=").append(postParam.getValue());
            }

            if (method.equals(RequestMethod.GET))
                answer = RequestsHelper.getHttp(url + params.get(0).getValue(), null);
            if (method.equals(RequestMethod.POST)) {
                String body = sb.toString();
                Mac mac = Mac.getInstance("HmacSHA512");
                mac.init(new SecretKeySpec(privateKey.getBytes(), "HmacSHA512"));
                String signature = new String(Hex.encodeHex(mac.doFinal(body.getBytes())));
                List<NameValuePair> httpHeaders = new ArrayList<>();
                httpHeaders.add(new BasicNameValuePair("Key", publicKey));
                httpHeaders.add(new BasicNameValuePair("Sign", signature));
                answer = RequestsHelper.postHttp(url, params, httpHeaders);
            }

            JSONObject json = new JSONObject(answer);
            if (json.has("success") && json.getInt("success") == 0) {
                String tradePair = getPairFromParams(params);
                reportAnError(json, tradePair);
            }

            return answer;
        } catch (Exception e) {
            Logger.logException("While sending private request", e, true);
        }
        return "Blyooooo";
    }

    private String getError(JSONObject answer) {

        // for post method`
        if (answer.has("code")) {
            int code = answer.getInt("code");
            if (code == 804 || code == 807 || code == 831 || code == 0 || code ==832)
                return answer.getString("error");
        }

        // for some exceptiom in get method (in future)
        String error = answer.getString("error");
        if (error.equals(""))
            return answer.getString("error");

        return null;

    }

    private String getPairFromParams(List<NameValuePair> params) {
        List<NameValuePair> validParams = params.stream().filter(e -> e.getName().equals("pair")).collect(Collectors.toList());
        String tradePair = "";
        if (!validParams.isEmpty())
            tradePair = validParams.get(0).getValue();
        return tradePair;
    }


    private void reportAnError(JSONObject json, String tradePair) {
        System.out.println("Answer: " + json + ". " + new Date());
        String error = getError(json);
        if (error != null)
            SenderMessage.sendMessage(Resources.ALERT_BOT_TOKEN, Resources.ALERT_CHAT_ID,
                    Logger.messageToAdmin(Stocks.Tidex, json.toString(), tradePair));

    }
}
