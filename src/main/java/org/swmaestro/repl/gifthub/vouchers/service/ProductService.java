package org.swmaestro.repl.gifthub.vouchers.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.swmaestro.repl.gifthub.exception.BusinessException;
import org.swmaestro.repl.gifthub.exception.ErrorCode;
import org.swmaestro.repl.gifthub.vouchers.dto.ProductReadResponseDto;
import org.swmaestro.repl.gifthub.vouchers.entity.Product;
import org.swmaestro.repl.gifthub.vouchers.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	private final ProductRepository productRepository;

	public Product read(String productName) {
		return productRepository.findByName(productName);
	}

	public ProductReadResponseDto readById(Long id) {
		Optional<Product> product = productRepository.findById(id);
		if (product.isEmpty()) {
			throw new BusinessException("존재하지 않는 상품 입니다.", ErrorCode.NOT_FOUND_RESOURCE);
		}
		ProductReadResponseDto productReadResponseDto = mapToDto(product.get());
		return productReadResponseDto;
	}

	public ProductReadResponseDto mapToDto(Product product) {
		ProductReadResponseDto productReadResponseDto = ProductReadResponseDto.builder()
				.id(product.getId())
				.brandId(product.getBrand().getId())
				.name(product.getName())
				.description(product.getDescription())
				.isReusable(product.getIsReusable())
				.price(product.getPrice())
				.imageUrl(product.getImageUrl())
				.build();
		return productReadResponseDto;
	}
}
