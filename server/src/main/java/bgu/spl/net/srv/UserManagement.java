package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class UserManagement {
    private ConcurrentHashMap<String,String> userMap = new ConcurrentHashMap<String,String>();

    public ConcurrentHashMap<String,String> getUserMap(){
        return this.userMap;
    }

    public boolean validateUser(String userName, String password){
        boolean result = false;
        //user exists and password is right
        if (this.getUserMap().get(userName) != null){
            if (this.getUserMap().get(userName).equals(password)){
                result = true;
            }
        } else {
            //user doesn't exist, so is created now
            this.getUserMap().put(userName, password);
            result = true;
        }
        return result;
    }

}