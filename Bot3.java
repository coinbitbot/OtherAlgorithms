public class Bot3 {



    @Override
    public List<String> performOrders(String pair, double baseBalance, double minBalance, boolean buy, String privateKey, String publicKey) {

        List<String> performedOrders = new ArrayList<>();

        OrderBook orderBook = getOrderBook(pair);

        List<OrderBookItem> orderBookItems = buy ? orderBook.asks : orderBook.bids;

        double totalBase = 0;
        double superTotalTokens = 0;

        boolean last = false;
        double tokensAmount = 0;
        for (int i = 0; i < orderBookItems.size() - 1; i++) {
            OrderBookItem item = orderBookItems.get(i);

            totalBase += item.baseVolume;
            tokensAmount += item.coinVolume;
            superTotalTokens += item.coinVolume;

            if ((buy && totalBase >= baseBalance)) {
                last = true;
                double tempTotal = totalBase;


                totalBase -= totalBase - baseBalance;

                tokensAmount -= (tempTotal - baseBalance) / item.price;
                superTotalTokens -= (tempTotal - baseBalance) / item.price;
            }

            if (!last && !checkMinAmount(pair, item.baseVolume, last)) continue;
            else {
                String response = placeOrder(pair, item.price, subtractPercent(tokensAmount), buy, privateKey, publicKey);
                performedOrders.add(response);

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}

                cancelOrder(getOrderId(response), pair, privateKey, publicKey);

                if (last) break;
                tokensAmount = 0;
            }

        }

        String currency = buy ? PairUtils.getBase(getExchange(), pair) : PairUtils.getQuote(getExchange(), pair);
        double balance = getBalance(currency, false, privateKey, publicKey);
        if (checkMinAmount(currency, balance, last)) {
            performedOrders.addAll(performOrders(pair, baseBalance, minBalance, buy, privateKey, publicKey));
        }
        return performedOrders;
    }

    abstract Stocks getExchange();



    protected double subtractPercent(double amount) {
        return amount * 0.998;
    }

    protected boolean checkMinAmount(String pair, double value, boolean last) {
        if (pair.contains("USD")) return value > (last?0.15:1);
        if (pair.contains("BTC")) return value > (last?0.00015:0.001);
        if (pair.contains("ETH")) return value > (last?0.0035:0.03);
        return false;
    }

}
