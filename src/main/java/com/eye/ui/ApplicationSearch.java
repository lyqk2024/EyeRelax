package com.eye.ui;
import java.util.Locale;
/** Case-insensitive name/path search shared by app surfaces. */
final class ApplicationSearch {
 private ApplicationSearch(){}
    static boolean matches(String name,String path,String query){
        String search=query.strip().toLowerCase(Locale.ROOT);return search.isEmpty()||name.toLowerCase(Locale.ROOT).contains(search)||path.toLowerCase(Locale.ROOT).contains(search);
    }
}
