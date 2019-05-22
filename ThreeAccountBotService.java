package coinbot.modules.bots.services;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ThreeAccountBotServce{

    @Override
    protected void defineAccount(VolumeState state, Info info) {
        if (state.lastAccounts == null) state.lastAccounts = PerformAccount.ThirdAndSecond;
        if (state.getAccountsDepth().size()<3){
            ArrayList<Double> depth = new ArrayList<>(Arrays.asList(0d, 0d, 0d));
            state.accountsDepth= depth;
        }
        defineDoubleAccount(state, info);
    }




    private static void defineDoubleAccount(VolumeThread.VolumeState state, VolumeThread.Info info){
        List<Double> sorted = new ArrayList<>(state.accountsDepth);
        sorted.sort(Comparator.naturalOrder());
        if (info.isBuy){
            if (state.accountsDepth.get(0) >= state.accountsDepth.get(1) && state.accountsDepth.get(0) >= state.accountsDepth.get(2)) {
                if ((!state.lastAccounts.equals(PerformAccount.SecondAndFirst) && !state.lastAccounts.equals(PerformAccount.FirstAndSecond))) info.account = PerformAccount.FirstAndSecond;
                else info.account = PerformAccount.FirstAndThird;
            }else if (state.accountsDepth.get(1)>=state.accountsDepth.get(0) && state.accountsDepth.get(1)>=state.accountsDepth.get(2)){
                if ( (!state.lastAccounts.equals(PerformAccount.FirstAndSecond) && !state.lastAccounts.equals(PerformAccount.SecondAndFirst))) info.account = PerformAccount.SecondAndFirst;
                else info.account = PerformAccount.SecondAndThird;
            }else{
                if ((!state.lastAccounts.equals(PerformAccount.FirstAndThird) && !state.lastAccounts.equals(PerformAccount.ThirdAndFirst))) info.account = PerformAccount.ThirdAndFirst;
                else info.account = PerformAccount.ThirdAndSecond;
            }

        }else{
            if (state.accountsDepth.get(0) <= state.accountsDepth.get(1) && state.accountsDepth.get(0) <= state.accountsDepth.get(2)) {
                if ((!state.lastAccounts.equals(PerformAccount.SecondAndFirst) && !state.lastAccounts.equals(PerformAccount.FirstAndSecond))) info.account = PerformAccount.FirstAndSecond;
                else info.account = PerformAccount.FirstAndThird;
            }else if (state.accountsDepth.get(1)<=state.accountsDepth.get(0) && state.accountsDepth.get(1)<=state.accountsDepth.get(2)){
                if ((!state.lastAccounts.equals(PerformAccount.FirstAndSecond) && !state.lastAccounts.equals(PerformAccount.SecondAndFirst))) info.account = PerformAccount.SecondAndFirst;
                else info.account = PerformAccount.SecondAndThird;
            }else{
                if ( (!state.lastAccounts.equals(PerformAccount.FirstAndThird) && !state.lastAccounts.equals(PerformAccount.ThirdAndFirst))) info.account = PerformAccount.ThirdAndFirst;
                else info.account = PerformAccount.ThirdAndSecond;
            }
        }
        state.lastAccounts = info.account;
    }

    public static enum PerformAccount{
        FirstAndSecond,
        FirstAndThird,

        SecondAndThird,
        SecondAndFirst,

        ThirdAndFirst,
        ThirdAndSecond,
        OnlyFirst,
        OnlySecond,
        OnlyThird,
    }
}