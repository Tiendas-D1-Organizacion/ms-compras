package com.d1.compras;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ComprasApplication {
    public static void main(String[] args) { SpringApplication.run(ComprasApplication.class, args); }
    @Bean public RestTemplate restTemplate() { return new RestTemplate(); }
}

@Data
@Entity
class Orden {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private Long productoId;
    private Integer cantidad;
    private String estado;
}

interface OrdenRepository extends JpaRepository<Orden, Long> {}

@RestController
@RequestMapping("/")
class ComprasController {
    private final OrdenRepository repo;
    private final RestTemplate rest;
    private final RabbitTemplate rabbit;

    public ComprasController(OrdenRepository repo, RestTemplate rest, RabbitTemplate rabbit) {
        this.repo = repo;
        this.rest = rest;
        this.rabbit = rabbit;
    }

    @PostMapping
    public Orden crearOrden(@RequestBody Orden orden) {
        // 1. Comunicación Síncrona: Descontar stock en MS Inventario
        String invUrl = "http://localhost:8082/" + orden.getProductoId() + "/deducir?cantidad=" + orden.getCantidad();
        rest.put(invUrl, null);

        // 2. Guardar orden
        orden.setEstado("COMPLETADA");
        Orden guardada = repo.save(orden);

        // 3. Comunicación Asíncrona: Enviar mensaje al Broker para Notificaciones
        String mensaje = "{\"ordenId\":" + guardada.getId() + ", \"usuarioId\":" + orden.getUsuarioId() + "}";
        rabbit.convertAndSend("notificaciones_queue", mensaje);

        return guardada;
    }
}
