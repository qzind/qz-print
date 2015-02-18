<?php
require_once "RSAToolImpl.php";

class RSAToolFactory {

    public static function getRSATool() {
        return new RSAToolImpl();
    }

}
