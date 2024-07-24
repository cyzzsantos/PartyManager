package pt.isec.pd.tp.m2.logic.Tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateChecker {
    public static boolean validate(String args){
        Pattern pattern = Pattern.compile("^\\d{1,2}-\\d{1,2}-\\d{4}$");
        Matcher mat = pattern.matcher(args);

        return !mat.matches();
    }
}
