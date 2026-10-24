package com.inventario;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Users extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);
}
