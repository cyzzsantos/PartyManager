package pt.isec.pd.tp.m2.logic.Tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailChecker {

    public static boolean validate(String args){
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}");
        Matcher mat = pattern.matcher(args);

        return !mat.matches();
    }
}