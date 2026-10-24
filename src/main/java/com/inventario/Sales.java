package com.inventario;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Sales extends JpaRepository<Sale, Long> {}
