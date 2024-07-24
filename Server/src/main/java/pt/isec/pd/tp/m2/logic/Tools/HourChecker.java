package pt.isec.pd.tp.m2.logic.Tools;

public class HourChecker {
    public static boolean validate(String hour) {
        String[] hourParts = hour.split(":");
        if(hourParts.length != 2) {
            return true;
        }

        int hourPart;
        int minutePart;

        try {
            hourPart = Integer.parseInt(hourParts[0]);
            minutePart = Integer.parseInt(hourParts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        if(hourPart < 0 || hourPart > 23) {
            return true;
        }

        return minutePart < 0 || minutePart > 59;
    }
}
