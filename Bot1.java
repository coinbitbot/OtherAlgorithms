
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
    
    
    private double overrideLastPrice(double current, double heavyBuy, double lightSell) {
        System.out.println("left: " + heavyBuy);
        System.out.println("rig: " + lightSell);
        double part = current * 0.002;
        if (current <= heavyBuy) {
            current = heavyBuy;
            return (current + part);
        }

        if (current >= lightSell) {
            current = lightSell;
            return (current - part);
        }
        return current;
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
    
      public void destroy_walls(UserEntity user, InfoStock pairInfo) {
        try {
            deleteOrders(user, pairInfo.getPair(), CompositFunctions.affect_glass);
            //--------------------------------------------------------------------------------------
            pairInfo = stock_dispatcher.getService(user.getExchange()).getPairInfoById(user.getId(), pairInfo.getPair());
            if (pairInfo.isAffectGlass())
                task_manager.generetaTask(user, pairInfo, Function.place_walls, 0);
            //---------------------------------------------------------------------------------------

        } catch (Exception e) {
            Logger.logException("While deleting orders got answer (destroy_walls " + user.getExchange().toString() + " ): ", e, true);
            task_manager.generetaTask(user, pairInfo, Function.destroy_walls, Resources.DELAY);
        }
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
}
